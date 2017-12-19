package records.types.units;

import com.google.common.math.IntMath;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import records.data.unit.SingleUnit;
import records.data.unit.Unit;
import utility.Either;
import utility.Pair;

import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * As per "Types for Units-of-Measure: Theory and Practice" by Kennedy (CEFP '09),
 * and similar to our own records.data.unit.Unit class, a unit expression is
 * a map from units to non-zero integer powers, the difference here being simply that
 * a type can either be a concrete defined type, or a type variable awaiting inference.
 */
public class UnitExp
{
    // scalar if empty.  Maps single unit to power (can be negative, can't be zero)
    // A long is a type variable.
    private final TreeMap<Either<MutSingleUnit, SingleUnit>, Integer> units = new TreeMap<>();
    
    private UnitExp()
    {
        
    }

    public static final UnitExp SCALAR = new UnitExp();
    
    public UnitExp times(UnitExp rhs)
    {
        UnitExp u = new UnitExp();
        u.units.putAll(units);
        for (Entry<Either<MutSingleUnit, SingleUnit>, Integer> rhsUnit : rhs.units.entrySet())
        {
            // Decl to suppress nullness warnings on remapping function:
            @SuppressWarnings("nullness")
            int _dummy = u.units.merge(rhsUnit.getKey(), rhsUnit.getValue(), (l, r) -> (l + r == 0) ? null : l + r);
        }
        return u;
    }

    public UnitExp divideBy(UnitExp rhs)
    {
        return times(rhs.reciprocal());
    }


    public UnitExp reciprocal()
    {
        UnitExp u = new UnitExp();
        for (Entry<Either<MutSingleUnit, SingleUnit>, Integer> entry : units.entrySet())
        {
            u.units.put(entry.getKey(), - entry.getValue());
        }
        return u;
    }

    public UnitExp raisedTo(int power)
    {
        if (power == 0)
            return SCALAR;
        UnitExp u = new UnitExp();
        u.units.putAll(units);
        u.units.replaceAll((s, origPower) -> origPower * power);
        return u;
    }

    public @Nullable UnitExp unifyWith(UnitExp other)
    {
        // Here's my understanding of "Types for Units-of-Measure: Theory and Practice" by Kennedy (CEFP '09)
        // section 3.5/Figure 5.  First we invert one side and multiply together, then attempt to unify to one:
        
        UnitExp aimingForOne = new UnitExp();
        units.forEach((k, v) -> aimingForOne.units.put(k, v));
        other.units.forEach((k, v) ->
            // Very important: minus v to multiply by inverse of RHS:    
            aimingForOne.units.merge(k, -v, (a, b) -> a + b));
        // Take out any that resolved to zero:
        for (Iterator<Entry<Either<MutSingleUnit, SingleUnit>, Integer>> iterator = aimingForOne.units.entrySet().iterator(); iterator.hasNext(); )
        {
            if (iterator.next().getValue() == 0)
                iterator.remove();

        }

        @Nullable Map<MutSingleUnit, UnitExp> mapping = aimingForOne.unifyToOne();
        if (mapping != null)
        {
            // TODO if non-null, effect the mappings
            return this;
        }
        else
            return null;
    }

    /**
     * Note: this function modifies the current object.  Only call it on a temporary item.
     */
    private @Nullable Map<MutSingleUnit, UnitExp> unifyToOne()
    {
        // Continuing in Figure 5: we already have the normal form, although not neatly separated
        // into type vars and normal, and not sorted by power value.
        
        // If no type vars and no normal vars then unification was perfect and resulted in scalar,
        // so no type vars to resolve:
        if (units.isEmpty())
            return Collections.emptyMap();
        List<Pair<MutSingleUnit, Integer>> typeVars = units.entrySet().stream().flatMap(e -> e.getKey().either(mut -> Stream.of(new Pair<>(mut, e.getValue())), fixed -> Stream.empty())).collect(Collectors.toList());
        
        // If no type vars (and not empty overall) then we can't unify to one: fail
        if (typeVars.isEmpty())
            return null;
        // If one type var, it needs to divide all the remaining fixed units:
        if (typeVars.size() == 1)
        {
            int powerOfTypeVar = typeVars.get(0).getSecond();
            if (units.entrySet().stream().filter(e -> e.getKey().isRight()).allMatch(e -> Math.abs(e.getValue()) % Math.abs(powerOfTypeVar) == 0))
            {
                // Do it!
                UnitExp result = new UnitExp();
                units.forEach((k, v) -> {
                    k.either_(var -> {}, singleUnit -> result.units.put(Either.right(singleUnit), -v / powerOfTypeVar));
                });
                return Collections.singletonMap(typeVars.get(0).getFirst(), result);
            }
            else
                return null;
        }
        // Otherwise, we perform a mapping so that all other variables except the lowest absolute-power
        // type variable become modulo that power.  Must admit, don't quite understand this step...
        Pair<MutSingleUnit, Integer> lowestAbsPower = typeVars.stream().min(Comparator.comparing(p -> Math.abs(p.getSecond()))).orElse(null);
        if (lowestAbsPower == null)
            // Impossible!
            return null;
        @NonNull Pair<MutSingleUnit, Integer> lowestAbsPowerFinal = lowestAbsPower;
        units.replaceAll((k, v) -> k.equals(Either.right(lowestAbsPowerFinal.getFirst())) ? v : IntMath.mod(v, lowestAbsPowerFinal.getSecond()));
        // Round we go again:
        
        // TODO but we're discarding the change we just made!  Not right, need some tests here...
        return unifyToOne();
            
    }

    public @Nullable Unit toConcreteUnit()
    {
        Unit u = Unit.SCALAR;
        for (Entry<Either<MutSingleUnit, SingleUnit>, Integer> e : units.entrySet())
        {
            @Nullable Unit multiplier = e.getKey().<@Nullable Unit>either(l -> null, singleUnit -> new Unit(singleUnit).raisedTo(e.getValue()));
            if (multiplier == null)
                return null;
            u = u.times(multiplier);
        }
        return u;
    }

    public static UnitExp fromConcrete(Unit unit)
    {
        UnitExp unitExp = new UnitExp();
        unit.getDetails().forEach((k, v) -> {
            unitExp.units.put(Either.right(k), v);
        });
        return unitExp;
    }

    public static UnitExp makeVariable()
    {
        UnitExp unitExp = new UnitExp();
        unitExp.units.put(Either.left(new MutSingleUnit()), 1);
        return unitExp;
    }
}
