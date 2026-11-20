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
    @DisplayName("TC_BASE_06: Dichiarazione locale let Expr e risoluzione var expr nello scope")
    public void testTC_BASE_06_ValidLetBindingAndVarLookup() {
        String code = "let x = 10 in let y = 20 in x + y";
        assertEquals(new IntType(), parseAndCheck(code));
    }
}
