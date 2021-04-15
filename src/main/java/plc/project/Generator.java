package plc.project;

import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

public final class Generator implements Ast.Visitor<Void> {

    private final PrintWriter writer;
    private int indent = 0;

    public Generator(PrintWriter writer) {
        this.writer = writer;
    }

    private void print(Object... objects) {
        for (Object object : objects) {
            if (object instanceof Ast) {
                visit((Ast) object);
            } else {
                writer.write(object.toString());
            }
        }
    }

    private void newline(int indent) {
        writer.println();
        for (int i = 0; i < indent; i++) {
            writer.write("    ");
        }
    }

    @Override
    public Void visit(Ast.Source ast) {
        print("public class Main {");
        newline(0);
        newline(++indent);
        if(!ast.getFields().isEmpty()) {
            for(Ast.Field field : ast.getFields()) {
                print(field);
            }
            newline(indent);
        }
        print("public static void main(String[] args) {");
        newline(++indent);
        print("System.exit(new Main().main());");
        newline(--indent);
        print("}");
        newline(0);
        newline(indent);
        for(Ast.Method method : ast.getMethods()) {
            print(method);
        }
        newline(0);
        newline(--indent);
        print('}');

        return null;
    }

    @Override
    public Void visit(Ast.Field ast) {
        print(ast.getVariable().getType().getJvmName(), "  ", ast.getVariable().getJvmName());
        if(ast.getValue().isPresent()) {
            print(" = ", ast.getValue());
        }
        return null;
    }

    @Override
    public Void visit(Ast.Method ast) {
        print(ast.getFunction().getReturnType().getJvmName());
        print(" ", ast.getFunction().getJvmName(), "(");
        for(int i = 0; i <= ast.getParameterTypeNames().size() - 1; i ++) {
            String type = ast.getFunction().getParameterTypes().get(i).getJvmName();
            String name = ast.getParameters().get(i);
            print(type, " ", name);
            if(i != ast.getParameterTypeNames().size() - 1) {
                print(", ");
            }
        }
        print(") {");
        newline(++indent);
        for(int i = 0; i <= ast.getStatements().size() - 1; i ++) {
            if(i != 0) {
                newline(indent);
            }
            print(ast.getStatements().get(i));
        }
        newline(--indent);
        print("}");
        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Expression ast) {
        print(ast.getExpression(), ";");
        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Declaration ast) {
        print(ast.getVariable().getType().getJvmName(), " ", ast.getVariable().getName());
        if(ast.getValue().isPresent()) {
            print(" = ", ast.getValue().get());
        }
        print(";");
        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Assignment ast) {
        print(ast.getReceiver(), " = ", ast.getValue());
        return null;
    }

    @Override
    public Void visit(Ast.Stmt.If ast) {
        print("if (", ast.getCondition(), ") {");
        if(!ast.getThenStatements().isEmpty()) {
            newline(++indent);
            for(int i = 0; i <= ast.getThenStatements().size() - 1; i ++) {
                if(i != 0) {
                    newline(indent);
                }
                print(ast.getThenStatements().get(i));
            }
            newline(--indent);
        }
        print("}");
        if(!ast.getElseStatements().isEmpty()) {
            print(" else {");
            newline(++indent);
            for(int i = 0; i <= ast.getElseStatements().size() - 1; i ++) {
                if(i != 0) {
                    newline(indent);
                }
                print(ast.getElseStatements().get(i));
            }
            newline(--indent);
            print("}");
        }
        return null;
    }

    @Override
    public Void visit(Ast.Stmt.For ast) {
        print("for (", ast.getName(), " : ", ast.getValue(), ") {");
        if(!ast.getStatements().isEmpty()) {
            newline(++indent);
            for(int i = 0; i <= ast.getStatements().size() - 1; i ++) {
                if(i != 0) {
                    newline(indent);
                }
                print(ast.getStatements().get(i));
            }
            newline(--indent);
        }
        print("}");
        return null;
    }

    @Override
    public Void visit(Ast.Stmt.While ast) {
        print("while (", ast.getCondition(), ") {");
        if(!ast.getStatements().isEmpty()) {
            newline(++indent);
            for(int i = 0; i <= ast.getStatements().size() - 1; i ++) {
                if(i != 0) {
                    newline(indent);
                }
                print(ast.getStatements().get(i));
            }
            newline(--indent);
        }
        print("}");
        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Return ast) {
        print("return ", ast.getValue(), ";");
        return null;
    }

    @Override
    public Void visit(Ast.Expr.Literal ast) {
        Object literal = ast.getLiteral();
        if(ast.getType() == Environment.Type.NIL) {
            print("null");
        }else if(ast.getType() == Environment.Type.BOOLEAN) {
            print("" + (Boolean)literal);
        }else if(ast.getType() == Environment.Type.CHARACTER) {
            print("'" + (Character)literal + "'");
        }else if(ast.getType() == Environment.Type.STRING) {
            print("\"" + (String)literal + "\"");
        }else if(ast.getType() == Environment.Type.INTEGER) {
            String value = ((BigInteger)literal).toString();
            print(value);
        }else if(ast.getType() == Environment.Type.DECIMAL) {
            String value = ((BigDecimal)literal).toString();
            print(value);
        }
        return null;
    }

    @Override
    public Void visit(Ast.Expr.Group ast) {
        print("(", ast.getExpression(), ")");
        return null;
    }

    @Override
    public Void visit(Ast.Expr.Binary ast) {
        Ast.Expr left = ast.getLeft();
        Ast.Expr right = ast.getRight();
        String operator = " ";
        if(ast.getOperator() == "AND") {
            operator = " && ";
        }else if(ast.getOperator() == "OR") {
            operator = " || ";
        }else{
            operator = " " + ast.getOperator() + " ";
        }
        print(left, operator, right);
        return null;
    }

    @Override
    public Void visit(Ast.Expr.Access ast) {
        if(ast.getReceiver().isPresent()) {
            Ast.Expr receiver = ast.getReceiver().get();
            print(receiver, ".");
        }
        print(ast.getVariable().getJvmName());
        return null;
    }

    @Override
    public Void visit(Ast.Expr.Function ast) {
        if(ast.getReceiver().isPresent()) {
            Ast.Expr receiver = ast.getReceiver().get();
            print(receiver, ".");
        }
        print(ast.getFunction().getJvmName(), "(");
        for(int i = 0; i <= ast.getArguments().size() - 1; i ++) {
            print(ast.getArguments().get(i));
            if(i != ast.getArguments().size() - 1) {
                print(", ");
            }
        }
        print(")");
        return null;
    }

}
