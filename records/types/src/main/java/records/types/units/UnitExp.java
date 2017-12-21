package records.types.units;

import com.google.common.math.IntMath;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.unit.SingleUnit;
import records.data.unit.Unit;
import utility.ComparableEither;
import utility.Either;
import utility.Pair;

import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.StringJoiner;
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
    private final TreeMap<ComparableEither<MutUnitVar, SingleUnit>, Integer> units = new TreeMap<>();
    
    private UnitExp()
    {
        
    }
    
    public UnitExp(MutUnitVar singleUnitVar)
    {
        units.put(ComparableEither.left(singleUnitVar), 1);
    }

    public static final UnitExp SCALAR = new UnitExp();
    
    public UnitExp times(UnitExp rhs)
    {
        UnitExp u = new UnitExp();
        u.units.putAll(units);
        u.timesInPlace(rhs);
        return u;
    }

    private void timesInPlace(UnitExp rhs)
    {
        for (Entry<ComparableEither<MutUnitVar, SingleUnit>, Integer> rhsUnit : rhs.units.entrySet())
        {
            units.merge(rhsUnit.getKey(), rhsUnit.getValue(), (l, r) -> {
                return addButZeroIsNull(l, r);
            });
        }
    }

    private Integer addButZeroIsNull(Integer l, Integer r)
    {
        @SuppressWarnings("nullness") // Fine according to method contract
        @NonNull Integer added = (l + r == 0) ? null : l + r;
        return added;
    }

    public UnitExp divideBy(UnitExp rhs)
    {
        return times(rhs.reciprocal());
    }


    public UnitExp reciprocal()
    {
        UnitExp u = new UnitExp();
        for (Entry<ComparableEither<MutUnitVar, SingleUnit>, Integer> entry : units.entrySet())
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
        aimingForOne.units.putAll(units);
        other.units.forEach((k, v) ->
            // Very important: minus v to multiply by inverse of RHS:    
            aimingForOne.units.merge(k, -v, this::addButZeroIsNull));

        @Nullable Map<MutUnitVar, UnitExp> mapping = aimingForOne.unifyToOne();
        if (mapping != null)
        {
            // Actually, map will be at most size one:
            for (Entry<MutUnitVar, UnitExp> entry : mapping.entrySet())
            {
                entry.getKey().pointer = entry.getValue();
            }
            
            this.substituteMutVars();
            other.substituteMutVars();
            
            return this;
        }
        else
            return null;
    }

    // Makes sure all MutVar that point to a resolution get removed from the map
    private void substituteMutVars()
    {
        boolean substituted = false;
        do
        {
            substituted = false;
            for (Iterator<Entry<ComparableEither<MutUnitVar, SingleUnit>, Integer>> iterator = units.entrySet().iterator(); iterator.hasNext(); )
            {
                Entry<ComparableEither<MutUnitVar, SingleUnit>, Integer> entry = iterator.next();
                @Nullable UnitExp pointer = entry.getKey().<@Nullable UnitExp>either(mut -> mut.pointer, s -> null);
                if (pointer != null)
                {
                    // Must remove before doing times in place, otherwise modification exception:
                    iterator.remove();
                    timesInPlace(pointer.raisedTo(entry.getValue()));
                    substituted = true;
                    // Breaks the for/iterator loop
                    break;
                }
            }
        }
        while (substituted);
    }

    /**
     * Note: this function modifies the current object.  Only call it on a temporary item.
     */
    private @Nullable Map<MutUnitVar, UnitExp> unifyToOne()
    {
        // Continuing in Figure 5: we already have the normal form, although not neatly separated
        // into type vars and normal, and not sorted by power value.
        
        // If no type vars and no normal vars then unification was perfect and resulted in scalar,
        // so no type vars to resolve:
        if (units.isEmpty())
            return Collections.emptyMap();
        List<Pair<MutUnitVar, Integer>> typeVars = units.entrySet().stream().flatMap(e -> e.getKey().either(mut -> Stream.of(new Pair<>(mut, e.getValue())), fixed -> Stream.empty())).collect(Collectors.toList());
        
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
                    k.either_(var -> {}, singleUnit -> result.units.put(ComparableEither.right(singleUnit), -v / powerOfTypeVar));
                });
                return Collections.singletonMap(typeVars.get(0).getFirst(), result);
            }
            else
                return null;
        }
        // Otherwise, we perform a mapping so that all other variables except the lowest absolute-power
        // type variable become modulo that power.  Must admit, don't quite understand this step...
        Pair<MutUnitVar, Integer> lowestAbsPower = typeVars.stream().min(Comparator.comparing(p -> Math.abs(p.getSecond()))).orElse(null);
        if (lowestAbsPower == null)
            // Impossible!  We know typeVars.size() > 1 at this point
            return null;
        @NonNull Pair<MutUnitVar, Integer> lowestAbsPowerFinal = lowestAbsPower;
        for (Iterator<Entry<ComparableEither<MutUnitVar, SingleUnit>, Integer>> iterator = units.entrySet().iterator(); iterator.hasNext(); )
        {
            Entry<ComparableEither<MutUnitVar, SingleUnit>, Integer> entry = iterator.next();
            if (!entry.getKey().equals(Either.right(lowestAbsPowerFinal.getFirst())))
            {
                int mod = IntMath.mod(entry.getValue(), Math.abs(lowestAbsPowerFinal.getSecond()));
                if (mod == 0)
                    iterator.remove();
                else
                    entry.setValue(mod);
            }
        }
        // Round we go again:
        
        // TODO but we're discarding the change we just made!  Not right, need some tests here...
        return unifyToOne();
            
    }

    public @Nullable Unit toConcreteUnit()
    {
        substituteMutVars();
        Unit u = Unit.SCALAR;
        for (Entry<ComparableEither<MutUnitVar, SingleUnit>, Integer> e : units.entrySet())
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
            unitExp.units.put(ComparableEither.right(k), v);
        });
        return unitExp;
    }

    public static UnitExp makeVariable()
    {
        UnitExp unitExp = new UnitExp();
        unitExp.units.put(ComparableEither.left(new MutUnitVar()), 1);
        return unitExp;
    }

    @Override
    public String toString()
    {
        StringJoiner top = new StringJoiner("*");
        // First add positives:
        int[] pos = new int[] {0};
        units.forEach((u, p) -> {
            if (p >= 1)
            {
                if (p > 1)
                    top.add(etoString(u) + "^" + p);
                else
                    top.add(etoString(u));
                pos[0] += 1;
            }
        });
        int neg = units.size() - pos[0];
        String allUnits = pos[0] > 1 ? "(" + top.toString() + ")" : top.toString();
        if (neg > 0)
        {
            StringJoiner bottom = new StringJoiner("*");

            boolean showAsNegative = allUnits.isEmpty();

            units.forEach((u, p) ->
            {
                if (p < -1 || (p == -1 && showAsNegative))
                    bottom.add(etoString(u) + "^" + (showAsNegative ? p : -p));
                else if (p == -1)
                    bottom.add(etoString(u));
            });
            if (allUnits.isEmpty())
                allUnits = bottom.toString();
            else if (neg > 1)
                allUnits += "/(" + bottom + ")";
            else
                allUnits += "/" + bottom;

        }
        if (!allUnits.isEmpty())
            return allUnits;
        else
            return "1";
    }

    private String etoString(ComparableEither<MutUnitVar, SingleUnit> u)
    {
        return u.either(mut -> mut.toString(), singleUnit -> singleUnit.getName());
    }
}
