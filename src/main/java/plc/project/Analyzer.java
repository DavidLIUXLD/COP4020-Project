package plc.project;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
        String name = ast.getName();
        String typeName = ast.getTypeName();
        scope.defineVariable(name, name, Environment.getType(typeName),Environment.NIL);
        return null;
    }

    @Override
    public Void visit(Ast.Method ast) {
        String name = ast.getName();
        List<String> parTypeNames = ast.getParameterTypeNames();
        List<Environment.Type> parameters = new ArrayList<>();
        for(String parTypeName : parTypeNames) {
            parameters.add(Environment.getType(parTypeName));
        }
        ast.

    }

    @Override
    public Void visit(Ast.Stmt.Expression ast) {
        throw new UnsupportedOperationException();  // TODO
    }

    @Override
    public Void visit(Ast.Stmt.Declaration ast) {
        throw new UnsupportedOperationException();  // TODO
    }

    @Override
    public Void visit(Ast.Stmt.Assignment ast) {

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
        String variableName = ast.getName();
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
        Ast.Expr value = ast.getValue();

    }

    @Override
    public Void visit(Ast.Expr.Literal ast) {
        Object literal = ast.getLiteral();
        if (literal == null) {
            ast.setType(Environment.Type.NIL);
        } else if (literal instanceof Character) {
            ast.setType(Environment.Type.CHARACTER);
        } else if (literal instanceof String) {
            ast.setType(Environment.Type.STRING);
        } else if (literal instanceof Boolean) {
            ast.setType(Environment.Type.BOOLEAN);
        } else if (literal instanceof Integer) {
            BigInteger maxVal = BigInteger.valueOf(Integer.MAX_VALUE);
            BigInteger literalVal = BigInteger.valueOf(((Integer) literal).longValue());
            if (literalVal.compareTo(maxVal) > 0) {
                throw new RuntimeException("Provided Integer literal value out of bound");
            }
            ast.setType(Environment.Type.INTEGER);
        } else if (literal instanceof Double) {
            BigDecimal maxVal = BigDecimal.valueOf(Double.MAX_VALUE);
            BigDecimal literalVal = BigDecimal.valueOf(((Double) literal).longValue());
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
        String operator = ast.getOperator();
        Ast.Expr left = ast.getLeft();
        Ast.Expr right = ast.getRight();
        if(operator.matches("AND|OR")) {
            requireAssignable(left.getType(), Environment.Type.BOOLEAN);
            requireAssignable(right.getType(), Environment.Type.BOOLEAN);
            ast.setType(Environment.Type.BOOLEAN);
        }else if(operator.matches("[<>=!]=?")) {
            requireAssignable(left.getType(), Environment.Type.COMPARABLE);
            requireAssignable(right.getType(), Environment.Type.COMPARABLE);
            requireAssignable(left.getType(), right.getType());
            ast.setType(Environment.Type.BOOLEAN);
        }else if (operator.matches("\\+")) {
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
        String variableName = ast.getName();
        Environment.Variable value;
        if(ast.getReceiver().isPresent()) {
            Ast.Expr.Access objAst = (Ast.Expr.Access)ast.getReceiver().get();
            String objName = objAst.getName();
            Environment.Variable object = scope.lookupVariable(objName);
            value = object.getType().getScope().lookupVariable(variableName);
        }else {
            value = scope.lookupVariable(variableName);
        }
        ast.setVariable(value);
        return null;
    }

    @Override
    public Void visit(Ast.Expr.Function ast) {
        String functionName = ast.getName();
        int arity = ast.getArguments().size();
        Environment.Function function;
        if(ast.getReceiver().isPresent()) {
            Ast.Expr reciever = ast.getReceiver().get();
            visit(reciever);
            Ast.Expr.Access objAst = (Ast.Expr.Access) reciever;
            String objName = objAst.getName();
            Environment.Variable object = scope.lookupVariable(objName);
            function = object.getType().getScope().lookupFunction(functionName, arity);
        }else {
            function = scope.lookupFunction(functionName, arity);
        }
        if(function == null) {
            throw new RuntimeException("function " + functionName + " is not defined");
        }
        List<Ast.Expr> arguments = ast.getArguments();
        List<Environment.Type> parameterTypes = function.getParameterTypes();
        if(ast.getReceiver().isPresent()) {
            requireAssignable(arguments.get(0).getType(), ast.getReceiver().get().getType());
        }
        for(int i = 0; i <= arity - 1; i ++) {
            Ast.Expr argument = arguments.get(i);
            Environment.Type argType = argument.getType();
            Environment.Type paraType;
            if(ast.getReceiver().isPresent()) {
                paraType = parameterTypes.get(i + 1);
            }else{
                paraType = parameterTypes.get(i);
            }
            requireAssignable(argType, paraType);
            visit(argument);
        }
        ast.setFunction(function);
        return null;
    }

    public static void requireAssignable(Environment.Type target, Environment.Type type) {
        String typeName = type.getName();
        String targetName = target.getName();
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
