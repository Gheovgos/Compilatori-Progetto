package minifun;

import minifun.MiniFun.PolyType;
import minifun.MiniFun.Substitution;
import minifun.MiniFun.Type;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TypeEnvironment {

    private final Map<String, PolyType> gamma;

    public TypeEnvironment() {
        this.gamma = new HashMap<>();
    }

    private TypeEnvironment(Map<String, PolyType> oldGamma) {
        this.gamma = new HashMap<>(oldGamma);
    }

    /*
        Metodo overloaded per retrocompatibilità
        inserisce un monotipo wrappato in un politipo senza quantificatori.
     */
    public TypeEnvironment extend(String varName, Type type) {
        return extend(varName, PolyType.fromMono(type));
    }

    //Estende in modo immutabile il contesto con un politipo
    public TypeEnvironment extend(String varName, PolyType polyType) {
        TypeEnvironment next = new TypeEnvironment(this.gamma);
        next.gamma.put(varName, polyType);
        return next;
    }

     //Ricerca il politipo associato alla variabile nel contesto.
    public PolyType lookupPoly(String varName) {
        PolyType poly = gamma.get(varName);
        if (poly == null) {
            throw new TypeException("Variable not defined: '" + varName + "' is not present in the context.");
        }
        return poly;
    }

     //Metodo preservato per retrocompatibilità con i test unitari della baseline (TC_BASE_01)
    public Type lookup(String varName) {
        return lookupPoly(varName).monoType();
    }

     //Applica una sostituzione a ogni legame del contesto
    public TypeEnvironment applySubstitution(Substitution subst) {
        Map<String, PolyType> updated = new HashMap<>();
        for (Map.Entry<String, PolyType> entry : gamma.entrySet()) {
            updated.put(entry.getKey(), subst.apply(entry.getValue()));
        }
        return new TypeEnvironment(updated);
    }


     //Calcola l'insieme di tutte le variabili di tipo libere nel contesto.
    public Set<String> freeTypeVars() {
        Set<String> vars = new HashSet<>();
        for (PolyType poly : gamma.values()) {
            vars.addAll(poly.freeTypeVars());
        }
        return Collections.unmodifiableSet(vars);
    }
}