package minifun;

import java.util.*;
import java.util.regex.*;

public class MiniFun {
    //AST NODES
    interface Node {}
    interface Expr extends Node {}

    public interface Type { //il monotipo
        //Calcola l'insieme di variabili di tipo libere nel monotipo
        default Set<String> freeTypeVars() {
            if(this instanceof VarType v)
                return Set.of(v.name);
            else if (this instanceof FunType f) {
                Set<String> vars = new HashSet<>(f.left().freeTypeVars());
                vars.addAll(f.right().freeTypeVars());
                return Collections.unmodifiableSet(vars);
            }
            return Set.of();
        }
    }
    public record IntType() implements Type {
        @Override public String toString() {return "int";}
    }
    public record BoolType() implements Type {
        @Override public String toString() {return "bool";}
    }
    public record FunType(Type left, Type right) implements Type {
        @Override public String toString() {return "(" + left + "->" + right + ")";}
    }

    //Variabile di tipo
    public record VarType(String name) implements Type {
        public String toString() {return name;}
    }
    /*
        Un politipo è strutturato ad esempio come segue: forall alpha_1 ... alpha_n. tau
        tau è un monotipo, alpha_n è una variabile vincolata di tipo
     */
    public record PolyType(Set<String> quantifiedVars, Type monoType) {
        public PolyType {
            quantifiedVars = Collections.unmodifiableSet(new LinkedHashSet<>(quantifiedVars));
        }

        public static PolyType fromMono (Type monoType) {
            return new PolyType(Set.of(), monoType);
        }
        //metodo per calcolare le variabili di tipo libere
        public Set<String> freeTypeVars() {
            Set<String> vars = new HashSet<>(monoType.freeTypeVars());
            vars.removeAll(quantifiedVars);
            return Collections.unmodifiableSet(vars);
        }

        @Override
        public String toString() {
            if(quantifiedVars.isEmpty())
                return monoType.toString();
            StringBuilder sb = new StringBuilder();
            for(String v : quantifiedVars) {
                sb.append("forall ").append(v).append(". ");
            }
            sb.append(monoType);
            return sb.toString();
        }
    }

    //Sostituzione: funzione finita da variabili di tipo a monotipi
    public static final class Substitution {
        public final Map<String, Type> mappings;

        public Substitution(Map<String, Type> mappings) {
            this.mappings = Collections.unmodifiableMap(new HashMap<>(mappings));
        }

        public static Substitution empty() {
            return new Substitution(Map.of());
        }

        public static Substitution singleton(String varName, Type type) {
            return new Substitution(Map.of(varName, type));
        }

        public Map<String, Type> getMappings() {
            return mappings;
        }

        // Applica la sostituzione ad un monotipo: S(tau) dove tau è un monotipo
        public Type apply(Type type) {
            if (type instanceof VarType varType)
                return mappings.getOrDefault(varType.name, varType);
            else if (type instanceof FunType funType)
                return new FunType(apply(funType.left()), apply(funType.right()));
            return type;
        }

        //Applica la sostituzione a un politipo: S(sigma)
        //Le variabili quantificate universalmente non vengono sostituite
        public PolyType apply(PolyType polyType) {
            if (polyType.quantifiedVars().isEmpty())
                return PolyType.fromMono(apply(polyType.monoType()));
            Map<String, Type> restricted = new HashMap<>(this.mappings);
            for(String bound : polyType.quantifiedVars())
                restricted.remove(bound);
            Substitution restrictedSubst = new Substitution(restricted);
            return new PolyType(polyType.quantifiedVars(), restrictedSubst.apply(polyType.monoType()));
        }

        //Applica la sostituzione a un intero contesto dei tipi
        public TypeEnvironment apply(TypeEnvironment gamma) {
            return gamma.applySubstitution(this);
        }

        /*
          Composizione di sostituzioni: this.compose(other) corrisponde a (this o other),
          ossia applica prima 'other' e poi 'this': (S2 S1)(tau) = S2(S1(tau)).
         */
        public Substitution compose(Substitution other) {
            Map<String, Type> composed = new HashMap<>();
            for (Map.Entry<String, Type> entry : other.mappings.entrySet()) {
                composed.put(entry.getKey(), this.apply(entry.getValue()));
            }
            for (Map.Entry<String, Type> entry : this.mappings.entrySet()) {
                composed.putIfAbsent(entry.getKey(), entry.getValue());
            }
            return new Substitution(composed);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Substitution that)) return false;
            return Objects.equals(mappings, that.mappings);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mappings);
        }

        @Override
        public String toString() {
            return mappings.toString();
        }
    }


    // Basic values
    record IntExpr(int value) implements Expr {}
    record BoolExpr(boolean value) implements Expr {} // Uniamo True e False per comodità
    record VarExpr(String name) implements Expr {}

    // Operations
    record BinOpExpr(String op, Expr left, Expr right) implements Expr {}
    record IfExpr(Expr condition, Expr thenBranch, Expr elseBranch) implements Expr {}

    // Scope and functions
    record FunExpr(String parameter, Type parameterType, Expr body) implements Expr {} // fun x => t
    record AppExpr(Expr function, Expr arg) implements Expr {} // t t (es. f 3)
    record LetExpr(String varName, Expr value, Expr body) implements Expr {} // let x = t in t'
    record LetFunExpr(String funName, Type parameterType, Expr funBody, Expr body) implements Expr {} // letfun f x = t in t'


    enum TokenType {
        //KEYWORDS (must be before VAR)
        FUN("fun"), IF("if"), THEN("then"), ELSE("else"),
        LETFUN("letfun"), LET("let"), INTTYPE("int"), IN("in"), TRUE("true"), FALSE("false"),
         BOOLTYPE("bool"),

        //COMPOSED SYMBOLS (must be before the singles)
        ARROW("=>"), FUNTYPEARROW("->"), EQCMP("=="),

        //SINGLE SYMBOLS
        EQASSIGN("="), PLUS("\\+"), MINUS("-"), MULT("\\*"), LT("<"),
        TYPEOF(":"), LPAREN("\\("), RPAREN("\\)"),

        //GENERIC IDENTIFIERS AND EOF (always on bottom)
        VAR("[a-zA-Z][a-zA-Z0-9]*"), NUM("[0-9]+"), EOF("");

        public final String pattern;
        TokenType(String pattern) { this.pattern = pattern; }
    }

    record Token(TokenType type, String value) {}

    static class Lexer {
        static List<Token> tokenize(String input) {
            List<Token> tokens = new ArrayList<>();
            StringBuilder regex = new StringBuilder();
            for (TokenType t : TokenType.values()) {
                if (t != TokenType.EOF) regex.append(String.format("|(?<%s>%s)", t.name(), t.pattern));
            }
            Matcher m = Pattern.compile(regex.substring(1)).matcher(input);

            while (m.find()) {
                if (m.group().trim().isEmpty()) continue;
                for (TokenType t : TokenType.values()) {
                    if (t != TokenType.EOF && m.group(t.name()) != null) {
                        tokens.add(new Token(t, m.group(t.name())));
                        break;
                    }
                }
            }
            tokens.add(new Token(TokenType.EOF, ""));
            return tokens;
        }
    }

    //Recursive descending parser, written with the help of the AI
    static class Parser {
        private final List<Token> tokens;
        private int pos = 0;

        public Parser(List<Token> tokens) { this.tokens = tokens; }

        private Token current() { return tokens.get(pos); }
        private void advance() { if (pos < tokens.size() - 1) pos++; }

        private boolean match(TokenType expected) {
            if (current().type() == expected) { advance(); return true; }
            return false;
        }

        private Token consume(TokenType expected, String errorMsg) {
            Token tokenToReturn = current();
            if (!match(expected)) {
                throw new RuntimeException(errorMsg + " Got: " + current().value());
            }
            return tokenToReturn;
        }

        // Entry point: Read a term <t>
        public Expr parseExpr() {
            // 1. letfun f x = t in t'
            if (match(TokenType.LETFUN)) {
                String funName = consume(TokenType.VAR, "Expected function name after 'letfun'").value();
                Type annotatedType = null;
                String paramName = null;

                if (match(TokenType.TYPEOF)) {
                    // Sintassi classica: letfun f : (int -> int) = fun x : int => t1 in t2
                    annotatedType = parseType();
                } else if (current().type() == TokenType.VAR) {
                    // Sintassi (Algorithm W): letfun f x = t1 in t2
                    paramName = consume(TokenType.VAR, "Expected parameter name in letfun").value();
                    if (match(TokenType.TYPEOF)) {
                        annotatedType = parseType();
                    }
                }

                consume(TokenType.EQASSIGN, "Expected '=' in letfun declaration");
                Expr funBody = parseExpr();
                consume(TokenType.IN, "Expected 'in' in letfun declaration");
                Expr body = parseExpr();

                // Desugaring trasparente per l'Evaluator (Type Erasure):
                // avvolge t1 in un FunExpr mantenendo invariata la struttura di LetFunExpr
                if (paramName != null) {
                    funBody = new FunExpr(paramName, null, funBody);
                }
                return new LetFunExpr(funName, annotatedType, funBody, body);
            }
            // 2. let x = t in t'
            else if (match(TokenType.LET)) {
                String varName = current().value();
                consume(TokenType.VAR, "Expected variable name");
                consume(TokenType.EQASSIGN, "Expected '='");
                Expr value = parseExpr();
                consume(TokenType.IN, "Expected 'in'");
                Expr body = parseExpr();
                return new LetExpr(varName, value, body);
            }
            // 3. fun x => t
            else if (match(TokenType.FUN)) {
                String param = consume(TokenType.VAR, "Expected parameter name after 'fun'").value();
                Type paramType = null;
                if (match(TokenType.TYPEOF)) { // Annotazione di tipo opzionale (:)
                    paramType = parseType();
                }
                consume(TokenType.ARROW, "Expected '=>' in function definition");
                Expr body = parseExpr();
                return new FunExpr(param, paramType, body);
            }
            // 4. if t then t else t
            else if (match(TokenType.IF)) {
                Expr cond = parseExpr();
                consume(TokenType.THEN, "Expected 'then'");
                Expr thenBranch = parseExpr();
                consume(TokenType.ELSE, "Expected 'else'");
                Expr elseBranch = parseExpr();
                return new IfExpr(cond, thenBranch, elseBranch);
            }

            // If that's not one of the keyword, go down in the operators
            return parseRelOp();
        }

        // Relational operators: == , <
        private Expr parseRelOp() {
            Expr left = parseAddOp();
            while (current().type() == TokenType.EQCMP || current().type() == TokenType.LT) {
                String op = current().value();
                advance();
                left = new BinOpExpr(op, left, parseAddOp());
            }
            return left;
        }

        // Additional operators: + , -
        private Expr parseAddOp() {
            Expr left = parseMultOp();
            while (current().type() == TokenType.PLUS || current().type() == TokenType.MINUS) {
                String op = current().value();
                advance();
                left = new BinOpExpr(op, left, parseMultOp());
            }
            return left;
        }

        // Multiplicative operator: *
        private Expr parseMultOp() {
            Expr left = parseApp();
            while (match(TokenType.MULT)) {
                left = new BinOpExpr("*", left, parseApp());
            }
            return left;
        }

        // Function application
        private Expr parseApp() {
            Expr left = parseAtom();
            // Until the next token can start with an atomic expression, we are passing arguments
            while (isAtomStart()) {
                Expr arg = parseAtom();
                left = new AppExpr(left, arg);
            }
            return left;
        }

        //An atomic value is a base value, a variable, an expression in brackets
        private boolean isAtomStart() {
            TokenType t = current().type();
            return t == TokenType.NUM || t == TokenType.TRUE || t == TokenType.FALSE ||
                    t == TokenType.VAR || t == TokenType.LPAREN;
        }

        private Expr parseAtom() {
            if (current().type() == TokenType.NUM) {
                int val = Integer.parseInt(current().value());
                advance();
                return new IntExpr(val);
            } else if (match(TokenType.TRUE)) {
                return new BoolExpr(true);
            } else if (match(TokenType.FALSE)) {
                return new BoolExpr(false);
            } else if (current().type() == TokenType.VAR) {
                String name = current().value();
                advance();
                return new VarExpr(name);
            } else if (match(TokenType.LPAREN)) {
                Expr expr = parseExpr();
                consume(TokenType.RPAREN, "Expected closing brackets ')'");
                return expr;
            }
            throw new RuntimeException("Unexpected term: " + current().value());
        }

        // Method to parsing types (left associative for the row ->)
        private Type parseType() {
            Type type = parseBaseType();

            // If we got a '->', continue to concatenate at left
            while (match(TokenType.FUNTYPEARROW)) {
                Type rightType = parseBaseType();
                type = new FunType(type, rightType);
            }

            return type;
        }

        // Method for base types or types in brackets
        private Type parseBaseType() {
            if (match(TokenType.INTTYPE)) {
                return new IntType();
            }
            if (match(TokenType.BOOLTYPE)) {
                return new BoolType();
            }
            if (match(TokenType.LPAREN)) {
                Type type = parseType();
                consume(TokenType.RPAREN, "Expected ')' after the type");
                return type;
            }
            throw new RuntimeException("Parsing error: Invalid or missing type. Got: " + current().type() + " ('" + current().value() + "')");
        }
    }
}
