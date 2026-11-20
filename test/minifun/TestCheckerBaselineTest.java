package minifun;

import minifun.MiniFun.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;



public class TestCheckerBaselineTest {
    private TypeChecker typeChecker;
    private TypeEnvironment typeEnvironment;

    @BeforeEach
    public void setUp() {
        typeChecker = new TypeChecker();
        typeEnvironment = new TypeEnvironment();
    }


    //Driver: converte il codice sorgente in token con il lexer, costruisce l'AST con il parser ed esegue il typechecker partendo da un contesto dei tipi vuoto
    private Type parseAndCheck(String sourceCode) {
        List<Token> tokens = Lexer.tokenize(sourceCode);
        Parser parser = new Parser(tokens);
        Expr ast = parser.parseExpr();
        return typeChecker.check(ast, typeEnvironment);
    }



    //Unit Testing intra-class su TypeEnvironment

    @Test
    @DisplayName("TC_BASE_01: Lookup di variabile definita e immutabilità di TypeEnvironment")
    public void testTC_BASE_01_TypeEnvironmentExtendAndImmutability() {
        TypeEnvironment gamma1 = typeEnvironment.extend("x", new IntType());
        assertEquals(new IntType(), gamma1.lookup("x"));

        //Verifica che l'ambiente originale non sia stato mutato
        TypeException ex = assertThrows(TypeException.class, () -> typeEnvironment.lookup("x"));
        assertTrue(ex.getMessage().contains("Variable not defined: 'x'"));

        //Verifica lo shadowing lessicale in un nuovo ambiente esteso
        TypeEnvironment gamma2 = gamma1.extend("x", new BoolType());
        assertEquals(new BoolType(), gamma2.lookup("x"));
        assertEquals(new IntType(), gamma1.lookup("x"));
    }

    //Testing black box WECT (Casi positivi)
    @Test
    @DisplayName("TC_BASE_02: Tipi primitivi letterali (Interi e booleani True/False)")
    public void testTC_BASE_02_ValidLiterals() {
        assertEquals(new IntType(), parseAndCheck("32"));
        assertEquals(new BoolType(), parseAndCheck("true"));
        assertEquals(new BoolType(), parseAndCheck("false"));
    }

    @Test
    @DisplayName("TC_BASE_03: Operatori binari aritmetici (+, -, *) su operandi interi")
    public void testTC_BASE_03_ValidArithmeticOperations()
    {
        assertEquals(new IntType(), parseAndCheck("10 + 5"));
        assertEquals(new IntType(), parseAndCheck("10 - 5"));
        assertEquals(new IntType(), parseAndCheck("10 * 5"));
        assertEquals(new IntType(), parseAndCheck("(2 + 3) * (10 - 4)"));
    }

    @Test
    @DisplayName("TC_BASE_04: Operatori binari relazionali (==, <) su operandi interi")
    public void testTC_BASE_04_ValidOperationalOperations() {
        assertEquals(new BoolType(), parseAndCheck("5 == 5"));
        assertEquals(new BoolType(), parseAndCheck("3 < 10"));
    }

    @Test
    @DisplayName("TC_BASE_05: Costrutto condizionale if then else ben tipato")
    public void testTC_BASE_05_ValidIfThenElse() {
        assertEquals(new IntType(), parseAndCheck("if 3 < 5 then 100 else 200"));
        assertEquals(new BoolType(), parseAndCheck("if true then false else true"));
    }

    @Test
    @DisplayName("TC_BASE_06: Dichiarazione locale LetExpr e risoluzione VarExpr nello scope")
    public void testTC_BASE_06_ValidLetBindingAndVarLookup() {
        String code = "let x = 10 in let y = 20 in x + y";
        assertEquals(new IntType(), parseAndCheck(code));
    }

    @Test
    @DisplayName("TC_BASE_07: Definizione di funzione anonima tipizzata FunExpr")
    public void testTC_BASE_07_ValidAnonymousFunctionDefinition() {
        Type expectedType = new FunType(new IntType(), new BoolType());
        assertEquals(expectedType, parseAndCheck("fun x : int => x == 0"));
    }

    @Test
    @DisplayName("TC_BASE_08: Applicazione di funzione (AppExpr) e funzioni di ordine superiore")
    public void testTC_BASE_08_ValidFunctionApplicationAndHigherOrder() {

        //Applicazione diretta
        assertEquals(new IntType(), parseAndCheck("(fun x : int => x + 1) 5"));

        //Currying: funzione che restituisce una funzione
        String higherOrder = "let add = fun x : int => (fun y : int => x + y) in (add 3) 4";
        assertEquals(new IntType(), parseAndCheck(higherOrder));
    }

    @Test
    @DisplayName("TC_BASE_09: Dichiarazione e applicazione di funzione ricorsiva LetFunExpr")
    public void testTC_BASE_09_ValidRecursiveLetFun() {

        //Funzione ricorsiva di esempio: calcola la somma di tutti i numeri interi da 0 a n
        String code = "letfun sumToZero : (int -> int) ="
                    + "fun n : int => if n == 0 then 0 else n + (sumToZero (n - 1)) "
                    + "in sumToZero 5";
        assertEquals(new IntType(), parseAndCheck(code));
    }

    //Testing black box WECT (Classi non valide)
    @Test
    @DisplayName("TC_BASE_10: Errore per variabile non dichiarata nel contesto")
    public void testTC_BASE_10_InvalidUndefinedVariable() {
        String errorMsg = "Variable not defined: 'y' is not present in the context";
        TypeException e = assertThrows(TypeException.class, () -> parseAndCheck("y + 1"));
        assertTrue(e.getMessage().contains(errorMsg));
    }

    @Test
    @DisplayName("TC_BASE_11: Errore in operazione binaria con operando sx non intero")
    public void testTC_BASE_11_InvalidBinOpLeftOperandNotInt() {
        String errorMsg = "Type error in the operation '+'. Expected (int, int), but got (bool, int).";
        TypeException e = assertThrows(TypeException.class, () -> parseAndCheck("true + 23"));
        assertTrue(e.getMessage().contains(errorMsg));
    }

    @Test
    @DisplayName("TC_BASE_12: Errore in operazione binaria con operando dx non intero")
    public void testTC_BASE_12_InvalidBinOpRightOperandNotInt() {
        String errorMsg = "Type error in the operation '+'. Expected (int, int), but got (int, bool).";
        TypeException e = assertThrows(TypeException.class, () -> parseAndCheck("23 + false"));
        assertTrue(e.getMessage().contains(errorMsg));
    }

    @Test
    @DisplayName("TC_BASE_13: Errore in if per condizione non booleana")
    public void testTC_BASE_13_InvalidConditionNotBool() {
        String errorMsg = "Type error in the if condition: Expected bool but got int";
        TypeException e = assertThrows(TypeException.class, () -> parseAndCheck("if 10 + 2 then 1 else 0"));
        assertTrue(e.getMessage().contains(errorMsg));
    }

    @Test
    @DisplayName("TC_BASE_14: Errore in if per then ed else con tipo differente")
    public void testTC_BASE_14_InvalidIfBranchesTypeMismatch() {
        String errorMsg = "Type error in branches: then and else branch must be of the same type but got then of type int and else of type bool";
        TypeException e = assertThrows(TypeException.class, () -> parseAndCheck("if true then 1 else false"));
        assertTrue(e.getMessage().contains(errorMsg));
    }

    @Test
    @DisplayName("TC_BASE_15: Errore in AppExpr per applicazione di un termine che non è una funzione")
    public void testTC_BASE_15_InvalidApplicationOfNonFunction() {
        String errorMsg = "is not a function";
        TypeException e = assertThrows(TypeException.class, () -> parseAndCheck("let x = 5 in x 10"));
        assertTrue(e.getMessage().contains(errorMsg));
    }

    @Test
    @DisplayName("TC_BASE_16: Errore in AppExpr per argomento di tipo incompatibile con il tipo del parametro formale")
    public void testTC_BASE_16_InvalidApplicationArgumentTypeMismatch() {
        String errorMsg = "Type error: Expected parameter of type int but got type bool";
        TypeException e = assertThrows(TypeException.class, () -> parseAndCheck("let f = fun x : int => x + 1 in f true"));
        assertTrue(e.getMessage().contains(errorMsg));
    }

    @Test
    @DisplayName("TC_BASE_17: Errore in let fun expr quando il tipo annotato non è una funzione")
    public void testTC_BASE_17_InvalidLetFunAnnotatedTypeNotFunction() {
        String code = "letfun f : int = fun x : int => x in f";
        TypeException ex = assertThrows(TypeException.class, () -> parseAndCheck(code));
        assertTrue(ex.getMessage().contains("Type error: f is not a function"));
    }

    @Test
    @DisplayName("TC_BASE_18: Errore in let fun expr quando il corpo non rispetta la firma annotata")
    public void testTC_BASE_18_InvalidLetFunBodyTypeMismatch() {
        String errorMsg = "Type error: the type of f does not correspond to the type of";
        String code = "letfun f : (int -> bool) = fun x : int => x + 1 in f 5";                 //Dichiarata come (int -> bool) ma il corpo restituisce int (x + 1)
        TypeException ex = assertThrows(TypeException.class, () -> parseAndCheck(code));
        assertTrue(ex.getMessage().contains(errorMsg));
    }

    //White-Box testing: structural coverage per rami non raggiungibili dal parser

    @Test
    @DisplayName("TC_BASE_19: Copertura ramo di default per operatore binario sconosciuto su AST")
    public void testTC_BASE_19_WhiteBoxUnknownOperator() {
        Expr invalidBinOp = new BinOpExpr("/", new IntExpr(10), new IntExpr(2));
        TypeException e = assertThrows(TypeException.class, () -> typeChecker.check(invalidBinOp, typeEnvironment));
        assertTrue(e.getMessage().contains("Unknown operator: /"));
    }

    @Test
    @DisplayName("TC_BASE_20: Copertura ramo di default per nodo Expr non supportato dal type checker")
    public void testTC_BASE_20_WhiteBoxUnsupportedExprNode() {
        Expr invalidExpr = new Expr() {};
        RuntimeException e = assertThrows(RuntimeException.class, () -> typeChecker.check(invalidExpr, typeEnvironment));
        assertTrue(e.getMessage().contains("Node not supported by the TypeChecker"));
    }
}
