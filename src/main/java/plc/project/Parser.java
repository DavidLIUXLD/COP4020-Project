package plc.project;

import plc.homework.Token;
import plc.homework.ParseException;
import sun.awt.image.ImageWatched;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

/**
 * The parser takes the sequence of tokens emitted by the lexer and turns that
 * into a structured representation of the program, called the Abstract Syntax
 * Tree (AST).
 *
 * The parser has a similar architecture to the lexer, just with {@link Token}s
 * instead of characters. As before, {@link #peek(Object...)} and {@link
 * #match(Object...)} are helpers to make the implementation easier.
 *
 * This type of parser is called <em>recursive descent</em>. Each rule in our
 * grammar will have it's own function, and reference to other rules correspond
 * to calling that functions.
 */
public final class Parser {

    private final TokenStream tokens;

    public Parser(List<Token> tokens) {
        this.tokens = new TokenStream(tokens);
    }

    /**
     * Parses the {@code source} rule.
     */
    public Ast.Source parseSource() throws ParseException {
        List<Ast.Field> fields = new ArrayList<>();
        List<Ast.Method> methods = new ArrayList<>();
        while(match("LET")) {
            fields.add(parseField());
        }
        while(match("DEF")) {
            methods.add(parseMethod());
        }
        return new Ast.Source(fields, methods);
    }

    /**
     * Parses the {@code field} rule. This method should only be called if the
     * next tokens start a field, aka {@code LET}.
     */
    public Ast.Field parseField() throws ParseException {
        if (!tokens.has(0)) {
            throw new ParseException("parsing out of bound", tokens.index);
        }
        if (!peek(Token.Type.IDENTIFIER)) {
            throw new ParseException("Field not started by Identifier", tokens.index);
        }
        String name = tokens.get(0).getLiteral();
        tokens.advance();
        if (!match("=")) {
            if(!match(";")) {
                throw new ParseException("statement not ended with ; ->" + tokens.get(0).getLiteral(), tokens.index);
            }
            return new Ast.Field(name, Optional.empty());
        }
        Ast.Expr value = parseExpression();
        System.out.println(value.toString());
        if (!match(";")) {
            throw new ParseException("statement not ended with ; ->" + tokens.get(0).getLiteral(), tokens.index);
        }
        return new Ast.Field(name, Optional.of(value));
    }

    /**
     * Parses the {@code method} rule. This method should only be called if the
     * next tokens start a method, aka {@code DEF}.
     */
    public Ast.Method parseMethod() throws ParseException {
        if(!tokens.has(0)) {
            throw new ParseException("parsing out of bound", tokens.index);
        }
        if(!peek(Token.Type.IDENTIFIER)) {
            throw new ParseException("Method not given identifier", tokens.index);
        }
        String name = tokens.get(0).getLiteral();
        tokens.advance();
        if(!match("(")) {
            throw new ParseException("parameter scope not initiated", tokens.index);
        }
        List<String> parameters = new ArrayList<>();
        List<Ast.Stmt> statements = new LinkedList<>();
        if(peek(Token.Type.IDENTIFIER)) {
            parameters.add(tokens.get(0).getLiteral());
            tokens.advance();
            while(tokens.has(0) && !peek(")")) {
                if(!match(",")) {
                    throw new ParseException("Arguments not seperated by comma", tokens.index);
                }
                if(!tokens.has(0)) {
                    throw new ParseException("unclosed argument scope", tokens.index);
                }
                if (peek(")")) {
                    throw new ParseException("comma is not followed by arguments", tokens.index);
                }
                parameters.add(tokens.get(0).getLiteral());
                tokens.advance();
            }
        }
        if(!match(")")) {
            throw new ParseException("unclosed parameter scope", tokens.index);
        }
        if(!match("DO")) {
            throw new ParseException("Scope not initiated by DO", tokens.index);
        }
        while(tokens.has(0) && !peek("END")) {
            statements.add(parseStatement());
        }
        if(!tokens.has(0)) {
            throw new ParseException("Scope not Ended by END", tokens.index);
        }
        tokens.advance();
        return new Ast.Method(name, parameters, statements);
    }

    /**
     * Parses the {@code statement} rule and delegates to the necessary method.
     * If the next tokens do not start a declaration, if, while, or return
     * statement, then it is an expression/assignment statement.
     */
    public Ast.Stmt parseStatement() throws ParseException {
        if (!tokens.has(0)) {
            throw new ParseException("parsing out of bound", tokens.index);
        }
        if (match("LET")) {
            return parseDeclarationStatement();
        }
        if (match("IF")) {
            return parseIfStatement();
        }
        if (match("FOR")) {
            return parseForStatement();
        }
        if (match("WHILE")) {
            return parseWhileStatement();
        }
        if (match("RETURN")) {
            return parseReturnStatement();
        }
        Ast.Expr expression = parseExpression();
        if(match("=")) {
            Ast.Expr value = parseExpression();
            if(!match(";")) {
                throw new ParseException("assignment not ended with ;", tokens.index);
            }
            return new Ast.Stmt.Assignment(expression,value);
        }
        if(!match(";")) {
            throw new ParseException("expression not ended with ;", tokens.index);
        }
        return new Ast.Stmt.Expression(expression);

    }

    /**
     * Parses a declaration statement from the {@code statement} rule. This
     * method should only be called if the next tokens start a declaration
     * statement, aka {@code LET}.
     */
    public Ast.Stmt.Declaration parseDeclarationStatement() throws ParseException {
        if (!tokens.has(0)) {
            throw new ParseException("parsing out of bound", tokens.index);
        }
        if (!peek(Token.Type.IDENTIFIER)) {
            throw new ParseException("Declaration not started by Identifier", tokens.index);
        }
        String name = tokens.get(0).getLiteral();
        tokens.advance();
        if (!match("=")) {
            return new Ast.Stmt.Declaration(name, Optional.empty());
        }
        Ast.Expr value = parseExpression();
        if (!match(";")) {
            throw new ParseException("statement not ended with ;", tokens.index);
        }
        return new Ast.Stmt.Declaration(name, Optional.of(value));
    }

    /**
     * Parses an if statement from the {@code statement} rule. This method
     * should only be called if the next tokens start an if statement, aka
     * {@code IF}.
     */
    public Ast.Stmt.If parseIfStatement() throws ParseException {
        if (!tokens.has(0)) {
            throw new ParseException("parsing out of bound", tokens.index);
        }
        Ast.Expr condition = parseExpression();
        if (!match("DO")) {
            throw new ParseException("Scope not initiated by DO", tokens.index);
        }
        List<Ast.Stmt> thenStatements = new LinkedList<>();
        List<Ast.Stmt> elseStatements = new LinkedList<>();
        while (!peek("ELSE") && !peek("END")) {
            thenStatements.add( parseStatement());
        }
        if (!tokens.has(0)) {
            throw new ParseException("Scope not ended", tokens.index);
        }
        if (match("ELSE")) {
            while (tokens.has(0) && !peek("END")) {
                elseStatements.add(parseStatement());
            }
        }
        if (!tokens.has(0)) {
            throw new ParseException("Scope not ended with END", tokens.index);
        }
        tokens.advance();
        return new Ast.Stmt.If(condition, thenStatements, elseStatements);
    }

    /**
     * Parses a for statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a for statement, aka
     * {@code FOR}.
     */
    public Ast.Stmt.For parseForStatement() throws ParseException {
        if (!tokens.has(0)) {
            throw new ParseException("parsing out of bound", tokens.index);
        }
        if (!peek(Token.Type.IDENTIFIER)) {
            throw new ParseException("FOR not started by Identifier", tokens.index);
        }
        String name = tokens.get(0).getLiteral();
        tokens.advance();
        if (!match("IN")) {
            throw new ParseException("Scope of condition bot initiated by IN", tokens.index);
        }
        Ast.Expr value = parseExpression();
        if (!match("DO")) {
            throw new ParseException("Scope not initiated by DO", tokens.index);
        }
        List<Ast.Stmt> statements = new LinkedList<>();
        while (tokens.has(0) && !peek("END")) {
            statements.add(parseStatement());
        }
        if (!tokens.has(0)) {
            throw new ParseException("scope not ended by END", tokens.index);
        }
        tokens.advance();
        return new Ast.Stmt.For(name, value, statements);

    }

    /**
     * Parses a while statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a while statement, aka
     * {@code WHILE}.
     */
    public Ast.Stmt.While parseWhileStatement() throws ParseException {
        if (!tokens.has(0)) {
            throw new ParseException("parsing out of bound", tokens.index);
        }
        Ast.Expr condition = parseExpression();
        if (!match("DO")) {
            throw new ParseException("Scope not initiated with DO", tokens.index);
        }
        List<Ast.Stmt> statements = new LinkedList<>();
        while (tokens.has(0) && !peek("END")) {
            statements.add(parseStatement());
        }
        if (!tokens.has(0)) {
            throw new ParseException("scope not ended by END", tokens.index);
        }
        tokens.advance();
        return new Ast.Stmt.While(condition, statements);
    }


    /**
     * Parses a return statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a return statement, aka
     * {@code RETURN}.
     */
    public Ast.Stmt.Return parseReturnStatement() throws ParseException {
        if (!tokens.has(0)) {
            throw new ParseException("parsing out of bound", tokens.index);
        }
        Ast.Expr expression = parseExpression();
        if(!match(";")){
            throw new ParseException("Return statement not ended with ;", tokens.index);
        }
        return new Ast.Stmt.Return(expression);
    }


    /**
     * Parses the {@code expression} rule.
     */
    public Ast.Expr parseExpression() throws ParseException {
        if(!tokens.has(0)) {
            throw new ParseException("parsing out of bound", tokens.index);
        }
        return parseLogicalExpression();
    }

    /**
     * Parses the {@code logical-expression} rule.
     */
    public Ast.Expr parseLogicalExpression() throws ParseException {
        if(!tokens.has(0)) {
            throw new ParseException("parsing out of bound", tokens.index);
        }
        Ast.Expr equalExp = parseEqualityExpression();
        if(peek("OR") || peek("AND")) {
            String operator = tokens.get(0).getLiteral();
            tokens.advance();
            Ast.Expr operandExp = parseAdditiveExpression();
            return new Ast.Expr.Binary(operator, equalExp, operandExp);
        }
        return equalExp;
    }


    /**
     * Parses the {@code equality-expression} rule.
     */
    public Ast.Expr parseEqualityExpression() throws ParseException {
        if(!tokens.has(0)) {
            throw new ParseException("parsing out of bound", tokens.index);
        }
        Ast.Expr additiveExp = parseAdditiveExpression();
        if(peek(Token.Type.OPERATOR) && (peek("==") | peek("!=") | peek("<=") | peek(">="))) {
            String operator = tokens.get(0).getLiteral();
            tokens.advance();
            Ast.Expr operandExp = parseAdditiveExpression();
            return new Ast.Expr.Binary(operator, additiveExp, operandExp);
        }
        return additiveExp;

    }

    /**
     * Parses the {@code additive-expression} rule.
     */
    public Ast.Expr parseAdditiveExpression() throws ParseException {
        if(!tokens.has(0)) {
            throw new ParseException("parsing out of bound", tokens.index);
        }
        Ast.Expr multiExp = parseMultiplicativeExpression();
        if(peek("+") || peek("-")) {
            String operator = tokens.get(0).getLiteral();
            tokens.advance();
            Ast.Expr operandExp = parseMultiplicativeExpression();
            return new Ast.Expr.Binary(operator,multiExp,operandExp);
        }
        return multiExp;
    }


    /**
     * Parses the {@code multiplicative-expression} rule.
     */
    public Ast.Expr parseMultiplicativeExpression() throws ParseException {
        if(!tokens.has(0)) {
            throw new ParseException("parsing out of bound", tokens.index);
        }
        Ast.Expr secondaryExp = parseSecondaryExpression();
        if(peek("*") || peek("/")) {
            String operator = tokens.get(0).getLiteral();
            tokens.advance();
            Ast.Expr operandExp = parseSecondaryExpression();
            return new Ast.Expr.Binary(operator,secondaryExp,operandExp);
            }
        return secondaryExp;
    }

    /**
     * Parses the {@code secondary-expression} rule.
     */
    public Ast.Expr parseSecondaryExpression() throws ParseException {
        if (!tokens.has(0)) {
            throw new ParseException("parsing out of bound", tokens.index);
        }
        Ast.Expr primaryExp = parsePrimaryExpression();
        if (!match(".")) {
            return primaryExp;
        }
        if (!peek(Token.Type.IDENTIFIER)) {
            throw new ParseException("unspecified field access", tokens.index);
        }
        String name = tokens.get(0).getLiteral();
        tokens.advance();
        if (!match("(")) {
            return new Ast.Expr.Access(Optional.of(primaryExp), name);
        }
        List<Ast.Expr> arguments = new LinkedList<>();
        if (!match(")")) {
            arguments.add(parseExpression());
            while (tokens.has(0) && !peek(")")) {
                if (!match(",")) {
                    throw new ParseException("arguments not seperated by comma", tokens.index);
                }
                if(!match(")")) {
                    throw new ParseException("unclosed argument scope", tokens.index);
                }
                if (peek(")")) {
                    throw new ParseException("comma is not followed by arguments", tokens.index);
                }
                arguments.add(parseExpression());
            }
            if (!tokens.has(0)) {
                throw new ParseException("unclosed function call", tokens.index);
            }
            tokens.advance();
        }

        return new Ast.Expr.Function(Optional.of(primaryExp), name, arguments);
    }

    /**
     * Parses the {@code primary-expression} rule. This is the top-level rule
     * for expressions and includes literal values, grouping, variables, and
     * functions. It may be helpful to break these up into other methods but is
     * not strictly necessary.
     */
    public Ast.Expr parsePrimaryExpression() throws ParseException {
        if(tokens.has(0)) {
            String tokenLiteral = tokens.get(0).getLiteral();
            if (
                    match("TRUE") |
                    match("FALSE")
            ) {
                return new Ast.Expr.Literal(new Boolean(tokenLiteral));
            }
            if(match("NIL")) {
                return null;
            }
            if (match(Token.Type.INTEGER)) {
                return new Ast.Expr.Literal(new BigInteger(tokenLiteral));
            }
            if (match(Token.Type.DECIMAL)) {
                return new Ast.Expr.Literal(new BigDecimal(tokenLiteral));
            }
            if (match(Token.Type.CHARACTER)) {
                tokenLiteral = tokenLiteral.replaceAll("\'", "");
                return new Ast.Expr.Literal(tokenLiteral.charAt(0));
            }
            if (match(Token.Type.STRING)) {
                tokenLiteral = tokenLiteral.replaceAll("\"","");
                tokenLiteral = tokenLiteral.replaceAll("\\\\b", "\b");
                tokenLiteral = tokenLiteral.replaceAll("\\\\n", "\n");
                tokenLiteral = tokenLiteral.replaceAll("\\\\r", "\r");
                tokenLiteral = tokenLiteral.replaceAll("\\\\t", "\t");
                return new Ast.Expr.Literal(tokenLiteral);
            }
            if (peek(Token.Type.IDENTIFIER)) {
                String name = tokenLiteral;
                if (match(Token.Type.IDENTIFIER, "(")) {
                    List<Ast.Expr> arguments = new LinkedList<>();
                    if(!match(")")) {
                        arguments.add(parseExpression());
                        while (tokens.has(0) && !peek(")")) {
                            if(!match(",")){
                                throw new ParseException("arguments not seperated by comma", tokens.index);
                            }
                            if(!tokens.has(0)) {
                                throw new ParseException("unclosed argument scope", tokens.index);
                            }
                            if(peek(")")){
                                throw new ParseException("comma is not followed by arguments", tokens.index);
                            }
                            arguments.add(parseExpression());
                        }
                        if(!tokens.has(0)) {
                            throw new ParseException("not closed function call", tokens.index);
                        }
                        tokens.advance();
                    }
                    return new Ast.Expr.Function(Optional.empty(), name, arguments);
                }else {
                    tokens.advance();
                    return new Ast.Expr.Access(Optional.empty(), name);
                }
            }
            if(match("(")) {
                Ast.Expr expression = parseExpression();
                if (!match(")")) {
                    throw new ParseException("unclosed ()", tokens.index);
                }
                return new Ast.Expr.Group(expression);
            }

        }
        if(tokens.has(0)) {
            throw new ParseException(tokens.get(0).getLiteral() + "not matching type for Prime expr", tokens.index);
        }
        throw new ParseException("parsing out of bound ", tokens.index);
    }


    /**
     * As in the lexer, returns {@code true} if the current sequence of tokens
     * matches the given patterns. Unlike the lexer, the pattern is not a regex;
     * instead it is either a {@link Token.Type}, which matches if the token's
     * type is the same, or a {@link String}, which matches if the token's
     * literal is the same.
     *
     * In other words, {@code Token(IDENTIFIER, "literal")} is matched by both
     * {@code peek(Token.Type.IDENTIFIER)} and {@code peek("literal")}.
     */
    private boolean peek(Object... patterns) {
        for(int i = 0; i < patterns.length; i ++){
            if(!tokens.has(i)) {
                return false;
            }else if(patterns[i] instanceof Token.Type) {
                if(patterns[i] != tokens.get(i).getType()){
                    return false;
                }
            }else if (patterns[i] instanceof String){
                if(!patterns[i].equals((tokens.get(i).getLiteral()))){
                    return false;
                }
            }else {
                throw new ParseException("not valid pattern object" , i);
            }
        }
        return true;
    }

    /**
     * As in the lexer, returns {@code true} if {@link #peek(Object...)} is true
     * and advances the token stream.
     */
    private boolean match(Object... patterns) {
        boolean peek = peek(patterns);
        if(peek) {
            for(int i = 0; i < patterns.length; i ++) {
                tokens.advance();
            }
        }
        return peek;
    }


    private static final class TokenStream {

        private final List<Token> tokens;
        private int index = 0;

        private TokenStream(List<Token> tokens) {
            this.tokens = tokens;
        }

        /**
         * Returns true if there is a token at index + offset.
         */
        public boolean has(int offset) {
            return index + offset < tokens.size();
        }

        /**
         * Gets the token at index + offset.
         */
        public Token get(int offset) {
            return tokens.get(index + offset);
        }

        /**
         * Advances to the next token, incrementing the index.
         */
        public void advance() {
            index++;
        }

    }

}
