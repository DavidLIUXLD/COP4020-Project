package plc.project;

import com.sun.org.apache.xpath.internal.operations.String;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * See the specification for information about what the different visit
 * methods should do.
 */
public final class Analyzer implements Ast.Visitor<Void> {

    public Scope scope;
    private Ast.Method method;

    public Analyzer(Scope parent) {
        scope = new Scope(parent);
        scope.defineFunction("print", "System.out.println", Arrays.asList(Environment.Type.ANY), Environment.Type.NIL, args -> Environment.NIL);
    }

    public Scope getScope() {
        return scope;
    }

    @Override
    public Void visit(Ast.Source ast) {
        List<Ast.Method> methods = ast.getMethods();
        List<Ast.Field> fields = ast.getFields();
        boolean foundMain = false;
        for(Ast.Field field : fields) {
            visit(field);
        }
        for(Ast.Method method : methods) {
            if(method.getName() == "main" &&
               method.getParameters().isEmpty()) {
                foundMain = true;
            }
            visit(method);
        }
        if(!foundMain) {
            throw new RuntimeException("No main method defined");
        }
        return null;
    }

    @Override
    public Void visit(Ast.Field ast) {
        if(ast.getValue().isPresent()) {
            Ast.Expr value = ast.getValue().get();
            visit(value);
            requireAssignable(value.getType(), Environment.getType(ast.getTypeName()));
        }
        java.lang.String name = ast.getName();
        java.lang.String typeName = ast.getTypeName();
        scope.defineVariable(name, name, Environment.getType(typeName),Environment.NIL);
        return null;
    }

    @Override
    public Void visit(Ast.Method ast) {
        java.lang.String name = ast.getName();
        List<java.lang.String> parTypeNames = ast.getParameterTypeNames();
        List<java.lang.String> paraNames = ast.getParameters();
        List<Environment.Type> parameters = new ArrayList<>();
        for(java.lang.String parTypeName : parTypeNames) {
            parameters.add(Environment.getType(parTypeName));
        }
        Environment.Type returns;
        if(ast.getReturnTypeName().isPresent()) {
            returns = Environment.getType(ast.getReturnTypeName().get());
        }else{
            returns = Environment.getType("Nil");
        }
        Function<List<Environment.PlcObject>, Environment.PlcObject> function = (arguments) -> {
            return Environment.NIL;
        };
        scope.defineFunction(name, name, parameters, returns, function);
        ast.setFunction(scope.lookupFunction(name, parameters.size()));
        try {
            scope = new Scope(scope);
            for (int i = 0; i <= parTypeNames.size() - 1; i ++) {
                java.lang.String paraName = paraNames.get(i);
                Environment.Type type = parameters.get(i);
                scope.defineVariable(paraName, type.getJvmName(), type, Environment.NIL);
            }
            scope.defineVariable("RETURNVALUE", returns.getJvmName(), returns, Environment.NIL);
            List<Ast.Stmt> statements = ast.getStatements();
            for (Ast.Stmt stmt : statements) {
                visit(stmt);
            }
        }finally {
            scope = scope.getParent();
        }
        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Expression ast) {
        Ast.Expr expr = ast.getExpression();
        if(!(expr instanceof Ast.Expr.Function)) {
            throw new RuntimeException("Not a function statement");
        }
        visit(expr);
        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Declaration ast) {
        java.lang.String name = ast.getName();
        Environment.Type type;
        if(ast.getTypeName().isPresent()) {
            type = Environment.getType(ast.getTypeName().get());
        }else if(ast.getValue().isPresent()) {
            Ast.Expr value = ast.getValue().get();
            visit(value);
            type = value.getType();
            requireAssignable(value.getType(), type);
        }else {
            throw new RuntimeException("Unidentified type");
        }
        scope.defineVariable(name, name, type, Environment.NIL);
        ast.setVariable(scope.lookupVariable(name));
        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Assignment ast) {
        Ast.Expr expr = ast.getReceiver();
        if(!(expr instanceof Ast.Expr.Access)) {
            throw new RuntimeException("Not an access expr for assignment");
        }
        visit(expr);
        Ast.Expr value = ast.getValue();
        visit(value);
        requireAssignable(expr.getType(), value.getType());
        return null;
    }

    @Override
    public Void visit(Ast.Stmt.If ast) {
        Ast.Expr condition = ast.getCondition();
        visit(condition);
        requireAssignable(Environment.Type.BOOLEAN, condition.getType());
        List<Ast.Stmt> thenStmts = ast.getThenStatements();
        if(thenStmts.isEmpty()) {
            throw new RuntimeException("missing then statements for If statement");
        }
        try {
            scope = new Scope(scope);
            for(Ast.Stmt stmt : thenStmts) {
                visit(stmt);
            }
        }finally {
            scope = scope.getParent();
        }
        if(!ast.getElseStatements().isEmpty()) {
            List<Ast.Stmt> elseStmts = ast.getElseStatements();
            try {
                scope = new Scope(scope);
                for(Ast.Stmt stmt : elseStmts) {
                    visit(stmt);
                }
            }finally {
                scope = scope.getParent();
            }
        }
        return null;
    }

    @Override
    public Void visit(Ast.Stmt.For ast) {
        Ast.Expr value = ast.getValue();
        requireAssignable(Environment.Type.INTEGER_ITERABLE, value.getType());
        java.lang.String variableName = ast.getName();
        try {
            scope = new Scope(scope);
            scope.defineVariable(variableName, variableName,Environment.Type.INTEGER_ITERABLE, Environment.NIL);
            for(Ast.Stmt stmt : ast.getStatements()) {
                visit(stmt);
            }
        }finally {
            scope  = scope.getParent();
        }
        return null;

    }

    @Override
    public Void visit(Ast.Stmt.While ast) {
        visit(ast.getCondition());
        requireAssignable(Environment.Type.BOOLEAN, ast.getCondition().getType());
        try{
            scope = new Scope(scope);
            for(Ast.Stmt stmt : ast.getStatements()) {
                visit(stmt);
            }
        } finally {
            scope = scope.getParent();
        }
        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Return ast) {
        Environment.Variable expectedReturn = scope.lookupVariable("RETURNVALUE");
        Ast.Expr expr = ast.getValue();
        visit(expr);
        requireAssignable(expr.getType(), expectedReturn.getType());
        return null;
    }

    @Override
    public Void visit(Ast.Expr.Literal ast) {
        Object literal = ast.getLiteral();
        System.out.println(literal.toString());
        if (literal == null) {
            ast.setType(Environment.Type.NIL);
        } else if (literal instanceof Character) {
            ast.setType(Environment.Type.CHARACTER);
        } else if (literal instanceof java.lang.String) {
            ast.setType(Environment.Type.STRING);
        } else if (literal instanceof Boolean) {
            ast.setType(Environment.Type.BOOLEAN);
        } else if (literal instanceof BigInteger) {
            BigInteger maxVal = BigInteger.valueOf(Integer.MAX_VALUE);
            BigInteger literalVal = (BigInteger) literal;
            if (literalVal.compareTo(maxVal) > 0) {
                throw new RuntimeException("Provided Integer literal value out of bound");
            }
            ast.setType(Environment.Type.INTEGER);
        } else if (literal instanceof BigDecimal) {
            BigDecimal maxVal = BigDecimal.valueOf(Double.MAX_VALUE);
            BigDecimal literalVal = (BigDecimal) literal;
            if (literalVal.compareTo(maxVal) > 0) {
                throw new RuntimeException("Provided Decimal literal value out of bound");
            }
            ast.setType(Environment.Type.DECIMAL);
        } else {
            throw new RuntimeException("Provided literal is of unmatched type");
        }
        return null;
    }

    @Override
    public Void visit(Ast.Expr.Group ast) {
        Ast.Expr expr = ast.getExpression();
        if(!(expr instanceof Ast.Expr.Binary)) {
            throw new RuntimeException("group expression does not contain binary expr");
        }
        visit((Ast.Expr.Binary) expr);
        return null;
    }

    @Override
    public Void visit(Ast.Expr.Binary ast) {
        java.lang.String operator = ast.getOperator();
        Ast.Expr left = ast.getLeft();
        Ast.Expr right = ast.getRight();
        visit(left);
        visit(right);
        if(operator.matches("AND|OR")) {
            requireAssignable(left.getType(), Environment.Type.BOOLEAN);
            requireAssignable(right.getType(), Environment.Type.BOOLEAN);
            ast.setType(Environment.Type.BOOLEAN);
        }else if(operator.matches("[<>=!]=?")) {
            requireAssignable(left.getType(), Environment.Type.COMPARABLE);
            requireAssignable(right.getType(), Environment.Type.COMPARABLE);
            requireAssignable(left.getType(), right.getType());
            ast.setType(Environment.Type.BOOLEAN);
        }else if (operator == "+") {
            Environment.Type leftType = left.getType();
            Environment.Type rightType = right.getType();
            if(leftType == Environment.Type.STRING || rightType == Environment.Type.STRING) {
                ast.setType(Environment.Type.STRING);
            }else {
                if(leftType != Environment.Type.INTEGER &&
                   leftType != Environment.Type.DECIMAL) {
                    throw new RuntimeException("left hand type not decimal or integer");
                }
                requireAssignable(rightType, leftType);
                ast.setType(leftType);
            }
        }else if (operator.matches("[-*/]")) {
            Environment.Type leftType = left.getType();
            Environment.Type rightType = right.getType();
            if(leftType != Environment.Type.INTEGER &&
               leftType != Environment.Type.DECIMAL) {
                throw new RuntimeException("left hand type not decimal or integer");
            }
            requireAssignable(rightType, leftType);
            ast.setType(leftType);
        }else {
            throw new RuntimeException("Unmatched operator");
        }
        return null;
    }

    @Override
    public Void visit(Ast.Expr.Access ast) {
        java.lang.String variableName = ast.getName();
        Environment.Variable value;
        if(ast.getReceiver().isPresent()) {
            Ast.Expr reciever = ast.getReceiver().get();
            visit(reciever);
            Ast.Expr.Access objAst = (Ast.Expr.Access)reciever;
            java.lang.String objName = objAst.getName();
            Environment.Variable object = scope.lookupVariable(objName);
            value = object.getType().getField(variableName);
        }else {
            value = scope.lookupVariable(variableName);
        }
        ast.setVariable(value);
        return null;
    }

    @Override
    public Void visit(Ast.Expr.Function ast) {
        java.lang.String functionName = ast.getName();
        int arity = ast.getArguments().size();
        Environment.Function function;
        if(ast.getReceiver().isPresent()) {
            Ast.Expr reciever = ast.getReceiver().get();
            visit(reciever);
            Ast.Expr.Access objAst = (Ast.Expr.Access) reciever;
            java.lang.String objName = objAst.getName();
            Environment.Variable object = scope.lookupVariable(objName);
            function = object.getType().getMethod(functionName, arity);
        }else {
            function = scope.lookupFunction(functionName, arity);
        }
        if(function == null) {
            throw new RuntimeException("function " + functionName + " is not defined");
        }
        List<Ast.Expr> arguments = ast.getArguments();
        List<Environment.Type> parameterTypes = function.getParameterTypes();
        for(int i = 0; i <= arity - 1; i ++) {
            Ast.Expr argument = arguments.get(i);
            visit(argument);
            Environment.Type argType = argument.getType();
            Environment.Type paraType;
            if(ast.getReceiver().isPresent()) {
                paraType = parameterTypes.get(i + 1);
            }else{
                paraType = parameterTypes.get(i);
            }
            requireAssignable(paraType, argType);
        }
        ast.setFunction(function);
        return null;
    }

    public static void requireAssignable(Environment.Type target, Environment.Type type) {
        java.lang.String typeName = type.getName();
        java.lang.String targetName = target.getName();
        if(targetName == typeName && target.getJvmName() == type.getJvmName()) {
            return;
        }
        if(targetName == "Any") {
            return;
        }

        if(targetName == "Comparable" &&
                (typeName == "Integer" ||
                 typeName == "Decimal" ||
                 typeName == "Character" ||
                 typeName == "String"))  {
            return;
        }
        throw new RuntimeException(targetName + " is not assignable to " + typeName);
    }

}
