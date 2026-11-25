package minifun;
import minifun.MiniFun.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.*;
import java.util.Set;

public class TypeChecker {

    private boolean isSameType(Type t1, Type t2)
    {
        return t1.equals(t2);
    }
    private int freshCounter = 0;

    // Genera una nuova variabile di tipo fresh (t0, t1, ecc)
    public VarType freshTypeVar()
    {
        return new VarType("t" + (freshCounter++));
    }

    // Sostituisce tutte le variabili legate di pt con variabili fresh
    // es. inst(forall a, b . a -> b) = t0 -> t1
    public Type inst(PolyType pt)
    {
        if(pt.quantifiedVars().isEmpty()) return pt.monoType();

        Map<String, Type> freshMap = new HashMap<>();

        for(String var : pt.quantifiedVars()) {
            freshMap.put(var, freshTypeVar());
        }

        return new Substitution(freshMap).apply(pt.monoType());
    }

    // Quantifica universalmente su tutte le variabili libere di t che non compaiono libere in gamma:
    // FTV(t) \ FTV(gamma)
    public PolyType gener(TypeEnvironment gamma, Type t)
    {
        Set<String> freeMonoType = new HashSet<>(t.freeTypeVars());

        freeMonoType.removeAll(gamma.freeTypeVars());

        return new PolyType(freeMonoType, t);
    }

    private Substitution bindVar(VarType var, Type target) {
        // Caso in cui si prova a unificare una variabile di tipo con se stessa
        if (target instanceof VarType otherVar && var.name().equals(otherVar.name())) {
            return Substitution.empty();
        }
        //impedisce tipi ricorsivi infiniti come alpha = int -> alpha
        if (target.freeTypeVars().contains(var.name())) {
            throw new TypeException(
                    "Occurs check failed: recursive infinite type detected unifying '"
                            + var.name() + "' with '" + target + "'");
        }
        return Substitution.singleton(var.name(), target);
    }

    // Calcola la sostituzione più generale S tale che S(t1) = S(t2)
    // Se non può, lancia TypeException
    public Substitution unify(Type t1, Type t2)
    {
        if ( (t1 instanceof IntType && t2 instanceof IntType) || (t1 instanceof BoolType && t2 instanceof BoolType)) return Substitution.empty();

        if (t1 instanceof VarType vt1) return bindVar(vt1, t2);
        if (t2 instanceof VarType vt2) return bindVar(vt2, t1);

        if (t1 instanceof FunType f1 && t2 instanceof FunType f2) {
            Substitution s1 = unify(f1.left(), f2.left());                          // Check dominio
            Substitution s2 = unify(s1.apply(f1.right()), s1.apply(f2.right()));

            return s2.compose(s1);
        }

        throw new TypeException("Cannot unify incompatible types: "+ t1 + " and "+t2);
    }

    //The function that translates Γ ⊢ t : τ
    public Type check(Expr expr, TypeEnvironment gamma)
    {
        return switch(expr)
        {
            //Rule Num: ⊢ n : int
            case IntExpr(int n) -> new IntType();

            //Rules BoolT and BoolF: ⊢ true : bool
            case BoolExpr(boolean b) -> new BoolType();

            //Rule Var: Γ(x) = τ
            case VarExpr(String name) -> gamma.lookup(name);

            //Rule Let: Γ ⊢ t1 : τ1 and Γ[x ↦ τ1] ⊢ t2 : τ2
            case LetExpr(String varName, Expr valueExpr, Expr body) -> {
                /*
                    1) I calculate the type of the expression to assign
                    2) I extend the type environment with the new type
                    3) I evaluate the body with the new environment
                 */
                Type t1 = check(valueExpr, gamma);
                TypeEnvironment newGamma = gamma.extend(varName, t1);
                yield check(body, newGamma);
            }

            case BinOpExpr(String op, Expr left, Expr right) -> {
                Type leftType = check(left, gamma);
                Type rightType = check(right, gamma);
                if (leftType instanceof IntType && rightType instanceof IntType) {
                    yield switch (op) {
                        case "+", "-", "*" -> new IntType();
                        case "==", "<" -> new BoolType();
                        default -> throw new TypeException("Unknown operator: " + op);
                    };

                } else {
                    throw new TypeException("Type error in the operation '" + op + "'. " +
                            "Expected (int, int), but got (" + leftType + ", " + rightType + ").");
                }
            }

            case IfExpr(Expr condition, Expr leftBranch, Expr rightBranch) -> {
                Type conditionType = check(condition, gamma);
                Type leftBranchType = check(leftBranch, gamma);
                Type rightBranchType = check(rightBranch, gamma);
                if(!(conditionType instanceof BoolType))
                    throw new TypeException("Type error in the if condition: Expected bool but got " + conditionType);
                else if(!isSameType(leftBranchType, rightBranchType))
                    throw new TypeException("Type error in branches: then and else branch must be of the same type but got then of type " + leftBranchType + " and else of type " + rightBranchType);
                else
                    yield leftBranchType;
            }

            case FunExpr(String parameter, Type parameterType, Expr body) -> {
                TypeEnvironment newGamma = gamma.extend(parameter, parameterType);
                Type returnType = check(body, newGamma);
                yield new FunType(parameterType, returnType);
            }

            case AppExpr(Expr function, Expr arg) -> {
                Type functionType = check(function, gamma);
                Type argType = check(arg, gamma);
                if(!(functionType instanceof FunType))
                    throw new TypeException("Type error: " + function + " is not a function");
                else if(!isSameType(argType, ((FunType) functionType).left()))
                    throw new TypeException("Type error: Expected parameter of type " + ((FunType) functionType).left() + " but got type " + argType);
                else
                    yield ((FunType) functionType).right();
            }

            case LetFunExpr(String functionName, Type functionType, Expr functionBody, Expr body) -> {
                if(!(functionType instanceof FunType))
                    throw new TypeException("Type error: " + functionName + " is not a function");
                TypeEnvironment recursiveGamma = gamma.extend(functionName, functionType);
                Type functionBodyType = check(functionBody, recursiveGamma);
                if(!isSameType(functionType, functionBodyType))
                    throw new TypeException("Type error: the type of " + functionName + " does not correspond to the type of " + functionBody);
                else
                    yield check(body, recursiveGamma); //I return the type of the principal block
            }

            default -> throw new RuntimeException("Node not supported by the TypeChecker: " + expr);
        };
    }
}
