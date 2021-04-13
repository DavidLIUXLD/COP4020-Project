package plc.project;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

public class Interpreter implements Ast.Visitor<Environment.PlcObject> {

    private Scope scope = new Scope(null);

    public Interpreter(Scope parent) {
        scope = new Scope(parent);
        scope.defineFunction("print", 1, args -> {
            System.out.println(args.get(0).getValue());
            return Environment.NIL;
        });
    }

    public Scope getScope() {
        return scope;
    }

    @Override
    public Environment.PlcObject visit(Ast.Source ast) {
        for(Ast.Field field: ast.getFields()) {
            visit(field);
        }
        Ast.Method main = null;
        for(Ast.Method method: ast.getMethods()) {
            visit(method);
            if(method.getName() == "main" && method.getParameters().size() == 0) {
                main = method;
            }
        }
        if(main == null) {
            throw new RuntimeException("No main function defined");
        }
        return scope.lookupFunction("main", 0).invoke(null);
    }

    @Override
    public Environment.PlcObject visit(Ast.Field ast) {
        String name = ast.getName();
        if(ast.getValue() == null) {
            scope.defineVariable(name, visit(ast.getValue().get()));
        }else {
            scope.defineVariable(name, Environment.NIL);
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Method ast) {
        String name = ast.getName();
        List<String> parameters = ast.getParameters();
        int arity = parameters.size();
        List<Ast.Stmt> statements = ast.getStatements();
        Function<List<Environment.PlcObject>, Environment.PlcObject> function = (arguments) -> {
            scope = new Scope(scope);
            for(int i = 0; i <= arity - 1; i ++) {
                scope.defineVariable(parameters.get(i), arguments.get(i));
            }
            try {
                for (Ast.Stmt stmt : statements) {
                    visit(stmt);
                }
            }catch (Return re) {
                scope = scope.getParent();
                return re.value;
            }
            scope = scope.getParent();
            return Environment.NIL;
        };
        scope.defineFunction(name, arity, function);
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Stmt.Expression ast) {
        visit(ast.getExpression());
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Stmt.Declaration ast) {
        if(ast.getValue().isPresent()) {
            scope.defineVariable(ast.getName(), visit(ast.getValue().get()));
        }else{
            scope.defineVariable(ast.getName(),Environment.NIL);
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Stmt.Assignment ast) {
        if(ast.getReceiver() == null) {
            throw new RuntimeException("Assignment target not given.");
        }
        if (!(ast.getReceiver() instanceof Ast.Expr.Access)) {
            throw new RuntimeException("Assignment target not a assignable.");
        }
        Ast.Expr.Access target = (Ast.Expr.Access)ast.getReceiver();
        String name = target.getName();
        Environment.PlcObject value = visit(ast.getValue());
        if(target.getReceiver() != null) {
            Environment.PlcObject receiver = visit(target.getReceiver().get());
            receiver.setField(name, value);
        }else {
            scope.lookupVariable(name).setValue(value);
        }
        return Environment.NIL;


    }

    @Override
    public Environment.PlcObject visit(Ast.Stmt.If ast) {
        if(requireType(Boolean.class, visit(ast.getCondition()))) {
            scope = new Scope(scope);
            for(Ast.Stmt stmt: ast.getThenStatements()) {
                visit(stmt);
            }
        }else {
            for(Ast.Stmt stmt: ast.getElseStatements()) {
                visit(stmt);
            }
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Stmt.For ast) {
        String name = ast.getName();
        Environment.PlcObject boundObj = visit(ast.getValue());
        Iterable bound = requireType(Iterable.class, boundObj);
        Iterator iterator = bound.iterator();
        while(iterator.hasNext()) {
            try {
                scope = new Scope(scope);
                Environment.PlcObject element = visit((Ast.Expr)iterator.next());
                scope.defineVariable(name, element);
                for(Ast.Stmt stmt: ast.getStatements()) {
                    visit(stmt);
                }

            }finally {
                scope = scope.getParent();
            }
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Stmt.While ast) {
        while(requireType(Boolean.class, visit(ast.getCondition()))) {
            try {
                scope = new Scope(scope);
                for(Ast.Stmt stmt: ast.getStatements()) {
                    visit(stmt);
                }
            }finally {
                scope = scope.getParent();
            }
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Stmt.Return ast) {
        throw new Return(visit(ast.getValue()));
    }

    @Override
    public Environment.PlcObject visit(Ast.Expr.Literal ast) {
        return Environment.create(ast.getLiteral());
    }

    @Override
    public Environment.PlcObject visit(Ast.Expr.Group ast) {
        return visit(ast.getExpression());
    }

    @Override
    public Environment.PlcObject visit(Ast.Expr.Binary ast) {
        Environment.PlcObject leftObject = visit(ast.getLeft());
        Environment.PlcObject rightObject = visit(ast.getRight());
        String operator = ast.getOperator();
        if(operator == "AND" || operator == "OR") {
            return visitBoolean(leftObject, rightObject, operator);
        }
        if(operator.matches("[<>]=?")) {
            return visitCompare(leftObject, rightObject, operator);
        }
        if(operator.matches("[=!]=")) {
            return visitEqual(leftObject, rightObject, operator);
        }
        if(operator == "+") {
            return visitPlus(leftObject, rightObject);
        }
        if(operator == "-") {
            return  visitMinus(leftObject, rightObject);
        }
        if(operator == "*") {
            return visitMulti(leftObject, rightObject);
        }
        if(operator == "/") {
            return visitDivide(leftObject, rightObject);
        }
        throw new RuntimeException("Unsupported operator for Binary expression passed");
    }

    @Override
    public Environment.PlcObject visit(Ast.Expr.Access ast) {
        String name = ast.getName();
        if(ast.getReceiver() != null) {
            Environment.PlcObject receiver = visit(ast.getReceiver().get());
            return receiver.getField(name).getValue();
        }else {
            return scope.lookupVariable(name).getValue();
        }
    }

    @Override
    public Environment.PlcObject visit(Ast.Expr.Function ast) {
        String name = ast.getName();
        List<Environment.PlcObject> arguments = new ArrayList<>();
        for(Ast.Expr argument : ast.getArguments()) {
            arguments.add(visit(argument));
        }
        Environment.PlcObject product;
        if(ast.getReceiver() != null) {
            Environment.PlcObject receiver = visit(ast.getReceiver().get());
            product = receiver.callMethod(name, arguments);
        }else {
            Environment.Function function = scope.lookupFunction(name, ast.getArguments().size());
            product = function.invoke(arguments);
        }
        return product;
    }

    private Environment.PlcObject visitBoolean(Environment.PlcObject leftObj,
                                               Environment.PlcObject rightObj, String operator) {
        Boolean left = requireType(Boolean.class, leftObj);
        Boolean right = requireType(Boolean.class, rightObj);
        if(operator == "AND") {
            return Environment.create(left && right);
        }else {
            return Environment.create(left || right);
        }
    }

    private Environment.PlcObject visitCompare(Environment.PlcObject leftObj,
                                               Environment.PlcObject rightObj, String operator) {
        Comparable left = requireType(Comparable.class, leftObj);
        Comparable right = requireType(Comparable.class, rightObj);
        if(left.getClass() != right.getClass()) {
            throw new RuntimeException("Comparison object type not compatible, left expects"
                    + left.getClass().toString() + " and right expects " + right.getClass().toString());
        }
        Boolean product = false;
        switch(operator) {
            case "<":
                product = left.compareTo(right) < 0;
                break;
            case ">":
                product = left.compareTo(right) > 0;
                break;
            case "<=":
                product = left.compareTo(right) <= 0;
                break;
            case ">=":
                product = left.compareTo(right) >= 0;
        }
        return Environment.create(product);
    }

    private Environment.PlcObject visitEqual(Environment.PlcObject leftObj,
                                             Environment.PlcObject rightObj, String operator) {
        boolean product = leftObj.equals(rightObj);
        if(operator == "==") {
            return Environment.create(product);
        }else {
            return Environment.create(!product);
        }
    }

    private Environment.PlcObject visitPlus(Environment.PlcObject leftObj, Environment.PlcObject rightObj) {
        if(String.class.isInstance(leftObj) || String.class.isInstance(rightObj)) {
            String left;
            String right;
            if(String.class.isInstance(leftObj)) {
                left = requireType(String.class, leftObj);
                right = rightObj.getValue().toString();
            }else {
                left = leftObj.getValue().toString();
                right = requireType(String.class, rightObj);
            }
            return Environment.create(left + right);
        }else if(BigInteger.class.isInstance(leftObj)) {
            int left = requireType(BigInteger.class, leftObj).intValue();
            int right = requireType(BigInteger.class, rightObj).intValue();
            return Environment.create(BigInteger.valueOf(left + right));
        }else if(BigDecimal.class.isInstance(leftObj)) {
            double left = requireType(BigDecimal.class, leftObj).doubleValue();
            double right = requireType(BigDecimal.class, rightObj).doubleValue();
            return Environment.create(new BigDecimal(left + right));
        }
        throw new RuntimeException("left expected integer or decimal but get " + leftObj.getClass().toString() + ".");
    }

    private Environment.PlcObject visitMinus(Environment.PlcObject leftObj,
                                             Environment.PlcObject rightObj) {
        if(BigInteger.class.isInstance(leftObj)) {
            int left = requireType(BigInteger.class, leftObj).intValue();
            int right = requireType(BigInteger.class, rightObj).intValue();
            return Environment.create(BigInteger.valueOf(left - right));
        }else if(BigDecimal.class.isInstance(leftObj)) {
            double left = requireType(BigDecimal.class, leftObj).doubleValue();
            double right = requireType(BigDecimal.class, rightObj).doubleValue();
            return Environment.create(new BigDecimal(left - right));
        }
        throw new RuntimeException("left expected integer or decimal but get " + leftObj.getClass().toString() + ".");
    }

    private Environment.PlcObject visitMulti(Environment.PlcObject leftObj,
                                             Environment.PlcObject rightObj) {
        if(BigInteger.class.isInstance(leftObj)) {
            int left = requireType(BigInteger.class, leftObj).intValue();
            int right = requireType(BigInteger.class, rightObj).intValue();
            return Environment.create((BigInteger.valueOf(left * right)));
        }else if(BigDecimal.class.isInstance(leftObj)) {
            double left = requireType(BigDecimal.class, leftObj).doubleValue();
            double right = requireType(BigDecimal.class, rightObj).doubleValue();
            return Environment.create(new BigDecimal(left * right));
        }
        throw new RuntimeException("left expected integer or decimal but get " + leftObj.getClass().toString() + ".");
    }

    private Environment.PlcObject visitDivide(Environment.PlcObject leftObj,
                                              Environment.PlcObject rightObj) {
        if(BigInteger.class.isInstance(leftObj)) {
            int left = requireType(BigInteger.class, leftObj).intValue();
            int right = requireType(BigInteger.class, rightObj).intValue();
            if(right == 0) {
                throw new RuntimeException("cannot divide by 0");
            }
            return Environment.create(BigInteger.valueOf(left / right));
        }else if(BigDecimal.class.isInstance(leftObj)) {
            double left = requireType(BigDecimal.class,leftObj).doubleValue();
            double right = requireType(BigDecimal.class, rightObj).doubleValue();
            if(right == 0.0) {
                throw new RuntimeException("cannot divide by 0.0");
            }
            return Environment.create(new BigDecimal(left / right).setScale(0, RoundingMode.HALF_EVEN));
        }
        throw new RuntimeException("left expected integer or decimal but get " + leftObj.getClass().toString() + ".");
    }
    /**
     * Helper function to ensure an object is of the appropriate type.
     */
    private static <T> T requireType(Class<T> type, Environment.PlcObject object) {
        if(type.isInstance(object.getValue())) {
            return type.cast(object.getValue());
        } else {
            throw new RuntimeException("Expected type " + type.getName() + ", received " + object.getValue().getClass().getName() + "."); //TODO
        }
    }

    /**
     * Exception class for returning values.
     */
    private static class Return extends RuntimeException {

        private final Environment.PlcObject value;

        private Return(Environment.PlcObject value) {
            this.value = value;
        }
    }
}
