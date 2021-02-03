package plc.homework;

public class main {
    public static void main(String[] args) {
        Lexer lexer = new Lexer("-five");
        lexer.peek("[A-Za-z_][A-Za-z0-9_-]*");
    }
}
