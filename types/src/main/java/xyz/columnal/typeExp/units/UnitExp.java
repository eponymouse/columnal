/*
 * Columnal: Safer, smoother data table processing.
 * Copyright (c) Neil Brown, 2016-2020, 2022.
 *
 * This file is part of Columnal.
 *
 * Columnal is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Columnal is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for 
 * more details.
 *
 * You should have received a copy of the GNU General Public License along 
 * with Columnal. If not, see <https://www.gnu.org/licenses/>.
 */

package xyz.columnal.typeExp.units;

import com.google.common.collect.ImmutableList;
import com.google.common.math.IntMath;
import org.checkerframework.checker.nullness.qual.KeyFor;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.unit.SingleUnit;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.styled.StyledShowable;
import xyz.columnal.styled.StyledString;
import xyz.columnal.utility.adt.ComparableEither;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.adt.Pair;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * As per "Types for Units-of-Measure: Theory and Practice" by Kennedy (CEFP '09),
 * and similar to our own xyz.columnal.data.unit.Unit class, a unit expression is
 * a map from units to non-zero integer powers, the difference here being simply that
 * a type can either be a concrete defined type, or a type variable awaiting inference.
 */
public final class UnitExp implements StyledShowable
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

    public UnitExp(SingleUnit singleUnit)
    {
        units.put(ComparableEither.right(singleUnit), 1);
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
        for (Entry<@KeyFor("rhs.units") ComparableEither<MutUnitVar, SingleUnit>, Integer> rhsUnit : rhs.units.entrySet())
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
        for (Entry<@KeyFor("this.units") ComparableEither<MutUnitVar, SingleUnit>, Integer> entry : units.entrySet())
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

        if (aimingForOne.unifyToOne())
        {
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
            for (Iterator<Entry<@KeyFor("this.units") ComparableEither<MutUnitVar, SingleUnit>, Integer>> iterator = units.entrySet().iterator(); iterator.hasNext(); )
            {
                Entry<@KeyFor("this.units") ComparableEither<MutUnitVar, SingleUnit>, Integer> entry = iterator.next();
                @Nullable UnitExp pointer = entry.getKey().<@Nullable UnitExp>either(mut -> mut.pointer, s -> null);
                if (pointer != null)
                {
                    // Must remove before doing times in place, otherwise modification exception:
                    // In turn, must take value before removal because entry.getValue() is not valid after iterator removal:
                    int value = entry.getValue();
                    iterator.remove();
                    timesInPlace(pointer.raisedTo(value));
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
    private boolean unifyToOne()
    {
        // Make sure any mut vars involved are pointing to null (substitute if not):
        substituteMutVars();
        
        // Continuing in Figure 5: we already have the normal form, although not neatly separated
        // into type vars and normal, and not sorted by power value.
        
        // If no type vars and no normal vars then unification was perfect and resulted in scalar,
        // so no type vars to resolve:
        if (units.isEmpty())
            return true;
        List<Pair<MutUnitVar, Integer>> typeVars = units.entrySet().stream().flatMap((Entry<@KeyFor("this.units") ComparableEither<MutUnitVar, SingleUnit>, Integer> e) -> e.getKey().either(mut -> Stream.of(new Pair<>(mut, e.getValue())), fixed -> Stream.<Pair<MutUnitVar, Integer>>empty())).collect(Collectors.<Pair<MutUnitVar, Integer>>toList());
        
        // If no type vars (and not empty overall) then we can't unify to one: fail
        if (typeVars.isEmpty())
            return false;
        // If one type var, it needs to divide all the remaining fixed units:
        if (typeVars.size() == 1)
        {
            int powerOfTypeVar = typeVars.get(0).getSecond();
            if (units.entrySet().stream().filter((Entry<@KeyFor("this.units") ComparableEither<MutUnitVar, SingleUnit>, Integer> e) -> e.getKey().isRight()).allMatch((Entry<@KeyFor("this.units") ComparableEither<MutUnitVar, SingleUnit>, Integer> e) -> Math.abs(e.getValue()) % Math.abs(powerOfTypeVar) == 0))
            {
                // It does divide all remaining fixed units: Do it!
                UnitExp result = new UnitExp();
                units.forEach((k, v) -> {
                    k.either_(var -> {}, singleUnit -> result.units.put(ComparableEither.right(singleUnit), -v / powerOfTypeVar));
                });
                typeVars.get(0).getFirst().pointer = result;
                return true;
            }
            else // Otherwise, we have e.g. A^2 = m^3, which we can't solve because we require integer exponents.
                return false;
        }
        // Otherwise, we perform a mapping so that all other variables except the lowest absolute-power
        // type variable become modulo that power.  Here's an example:
        // Imagine we have (capital letters are type variables, lower case are actual units:
        //   A^2 B^7 m^2 s^-7 = 1
        // We can perform a subsitution for a new variable by taking the lowest-exponent type variable and substituting
        // it for an equation that cancels out the rest usefully.  What the paper says is that the new variable will
        // be equal to the lowest-exponent type variable, multiplied by the negated floor division of the rest by that lowest-exponent.
        // In our example, this means:
        //   A = C^1 B^-3 m^-1 s^3
        // Therefore when raised to A's current power (the lowest exponent):
        //   A^2 = C^2 B^-6 m^-2 s^6
        // And once substituted, it effectively makes all exponents be modulo the lowest-exponent:
        //   (C^2 B^-6 m^-2 s^6) (B^7 m^2 s^-7) = 1
        //   C^2 B^1 s^-1 = 1
        // Now we go again, with B having lowest exponent:
        //   B = D^1 C^-2 s^1
        // Substituting:
        //   (D^1 C^-2 s^1) (C^2 s^-1) = 1
        //   D = 1
        // Then we will go into a different clause.
        // The main question I have is: how do we get the value for C, given that we eliminated it from the equations without substituting?
        // We can't ignore it because both A and B depend on C.  I think the answer is that after all unit inference, if inference
        // is successful, all units that point to nothing can be set to scalar.        
        
        Pair<MutUnitVar, Integer> lowestAbsPower = typeVars.stream().min(Comparator.comparing(p -> Math.abs(p.getSecond()))).orElse(null);
        if (lowestAbsPower == null)
            // Impossible!  We know typeVars.size() > 1 at this point
            return false;

        MutUnitVar newVar = new MutUnitVar();
        
        // Get rid of old unit:
        units.remove(ComparableEither.<MutUnitVar, SingleUnit>left(lowestAbsPower.getFirst()));
        // Ready new subsitution, but only insert at end (don't want to adjust its powers):
        UnitExp subst = new UnitExp();
        subst.units.put(ComparableEither.left(newVar), 1);
        // All others get the negated floored division:
        int divisor = lowestAbsPower.getSecond();
        units.forEach((k, v) -> subst.units.put(k, -(v / divisor)));
        lowestAbsPower.getFirst().pointer = subst;
        
        for (Iterator<Entry<@KeyFor("this.units") ComparableEither<MutUnitVar, SingleUnit>, Integer>> iterator = units.entrySet().iterator(); iterator.hasNext(); )
        {
            Entry<@KeyFor("this.units") ComparableEither<MutUnitVar, SingleUnit>, Integer> entry = iterator.next();
            int mod = IntMath.mod(entry.getValue(), Math.abs(lowestAbsPower.getSecond()));
            if (mod == 0)
            {
                iterator.remove();
            }
            else
                entry.setValue(mod);
        }
        // Having removed old var, put new var in with same power:
        units.put(ComparableEither.left(newVar), lowestAbsPower.getSecond());
        
        // Round we go again:
        return unifyToOne();
            
    }

    public @Nullable Unit toConcreteUnit()
    {
        substituteMutVars();
        Unit u = Unit.SCALAR;
        for (Entry<@KeyFor("this.units") ComparableEither<MutUnitVar, SingleUnit>, Integer> e : units.entrySet())
        {
            @Nullable Unit multiplier = e.getKey().<@Nullable Unit>either(l -> null, singleUnit -> new Unit(singleUnit).raisedTo(e.getValue()));
            if (multiplier == null)
                return null;
            u = u.times(multiplier);
        }
        return u;
    }

    public static UnitExp fromConcrete(Unit unit) throws InternalException
    {
        UnitExp unitExp = new UnitExp();
        for (Entry<SingleUnit, Integer> e : unit.getDetails().entrySet())
        {
            unitExp.units.put(ComparableEither.right((SingleUnit)e.getKey()), e.getValue());
        }
        return unitExp;
    }

    public static UnitExp makeVariable()
    {
        UnitExp unitExp = new UnitExp();
        unitExp.units.put(ComparableEither.left(new MutUnitVar()), 1);
        return unitExp;
    }

    @Override
    public StyledString toStyledString()
    {
        final ImmutableList.Builder<StyledString> topContent = ImmutableList.builder();
        // First add positives:
        int[] pos = new int[] {0};
        int[] neg = new int[] {0};
        units.forEach((u, p) -> {
            if (p >= 1)
            {
                if (p > 1)
                    topContent.add(StyledString.concat(etoString(u), StyledString.s("^" + p)));
                else
                    topContent.add(etoString(u));
                pos[0] += 1;
            }
            else if (p < 0)
            {
                neg[0] += 1;
            }
        });
        
        if (pos[0] == 0 && neg[0] == 0)
            return StyledString.s("1");
        
        ArrayList<StyledString> content = new ArrayList<>();
        content.add(StyledString.intercalate(StyledString.s("*"), topContent.build()));
        
        if (neg[0] == 0)
        {
            return StyledString.concat(content.toArray(new StyledString[0]));
        }
        else
        {
            if (pos[0] > 1)
            {
                // Need to bracket the top:
                content.add(0, StyledString.s("("));
                content.add(StyledString.s(")"));
            }
            
            ImmutableList.Builder<StyledString> bottom = ImmutableList.builder();

            boolean showAsNegative = pos[0] == 0;

            units.forEach((u, p) ->
            {
                if (p < -1 || (p == -1 && showAsNegative))
                    bottom.add(StyledString.concat(etoString(u), StyledString.s("^" + (showAsNegative ? p : -p))));
                else if (p == -1)
                    bottom.add(etoString(u));
            });
            if (showAsNegative)
                return StyledString.intercalate(StyledString.s("*"), bottom.build());
            else if (neg[0] > 1)
            {
                content.add(StyledString.s("/("));
                content.addAll(bottom.build());
                content.add(StyledString.s(")"));
            }
            else
            {
                content.add(StyledString.s("/"));
                content.addAll(bottom.build());
            }
            return StyledString.concat(content.toArray(new StyledString[0]));
        }
    }

    private static StyledString etoString(ComparableEither<MutUnitVar, SingleUnit> u)
    {
        return u.either(mut -> mut.toStyledString(), singleUnit -> StyledString.s(singleUnit.getName()));
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UnitExp unitExp = (UnitExp) o;
        return Objects.equals(units, unitExp.units);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(units);
    }

    public boolean isOnlyVars()
    {
        return this.units.keySet().stream().allMatch(Either::isLeft);
    }

    @Override
    public String toString()
    {
        return toStyledString().toPlain();
    }
}
