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

package xyz.columnal.data.unit;

import org.checkerframework.checker.nullness.qual.KeyFor;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.common.rationals.Rational;
import xyz.columnal.error.UserException;
import xyz.columnal.utility.adt.Pair;

import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.TreeMap;

/**
 * Created by neil on 09/12/2016.
 */
public class Unit
{
    // scalar if empty.  Maps single unit to power (can be negative, can't be zero)
    private final TreeMap<SingleUnit, Integer> units = new TreeMap<>();

    public Unit(SingleUnit singleUnit)
    {
        units.put(singleUnit, 1);
    }

    private Unit()
    {
    }

    public static final Unit SCALAR = new Unit();

    public Unit times(Unit rhs)
    {
        Unit u = new Unit();
        u.units.putAll(units);
        for (Entry<@KeyFor("rhs.units") SingleUnit, Integer> rhsUnit : rhs.units.entrySet())
        {
            u.units.merge(rhsUnit.getKey(), rhsUnit.getValue(), (l, r) -> {
                // Suppress null warning when we're removing key (fine with contract of method):
                @SuppressWarnings("nullness")
                @NonNull Integer added = (l + r == 0) ? null : l + r;
                return added;
            });
        }
        return u;
    }

    public Unit divideBy(Unit rhs)
    {
        return times(rhs.reciprocal());
    }


    public Unit raisedTo(int power)
    {
        if (power == 0)
            return SCALAR;
        Unit u = new Unit();
        u.units.putAll(units);
        u.units.replaceAll((s, origPower) -> origPower * power);
        return u;
    }

    public Unit rootedBy(int root) throws UserException
    {
        Unit u = new Unit();
        for (Entry<@KeyFor("this.units") SingleUnit, Integer> entry : units.entrySet())
        {
            if (entry.getValue() % root != 0)
            {
                throw new UserException("Cannot root unit " + entry.getKey() + "^" + entry.getValue() + " by " + root);
            }
            u.units.put(entry.getKey(), entry.getValue() / root);
        }
        return u;
    }

    public String getDisplayPrefix()
    {
        if (units.size() == 1)
        {
            Entry<@KeyFor("this.units") SingleUnit, Integer> only = units.entrySet().iterator().next();
            if (only.getValue() == 1)
                return only.getKey().getPrefix();
        }
        return "";
    }

    public String getDisplaySuffix()
    {
        if (units.size() == 1)
        {
            Entry<@KeyFor("this.units") SingleUnit, Integer> only = units.entrySet().iterator().next();
            if (only.getValue() == 1)
                return only.getKey().getSuffix();
        }
        return "";
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
                    top.add(u.toString() + "^" + p);
                else
                    top.add(u.toString());
                pos[0] += 1;
            }
        });
        int neg = units.size() - pos[0];
        String allUnits = pos[0] > 1 ? "(" + top.toString() + ")" : top.toString();
        if (allUnits.isEmpty())
            allUnits = "1";
        if (neg > 0)
        {
            StringJoiner bottom = new StringJoiner("*");

            units.forEach((u, p) ->
            {
                if (p < -1)
                    bottom.add(u.toString() + "^" + (-p));
                else if (p == -1)
                    bottom.add(u.toString());
            });
            
            
            if (neg > 1)
                allUnits += "/(" + bottom + ")";
            else
                allUnits += "/" + bottom;

        }
        return allUnits;
    }

    public String forDisplay()
    {
        if (units.isEmpty())
            return "";
        else
            return toString();
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Unit unit = (Unit) o;

        return units.equals(unit.units);
    }

    @Override
    public int hashCode()
    {
        return units.hashCode();
    }

    public Unit reciprocal()
    {
        Unit u = new Unit();
        for (Entry<@KeyFor("this.units") SingleUnit, Integer> entry : units.entrySet())
        {
            u.units.put(entry.getKey(), - entry.getValue());
        }
        return u;
    }

    // If this unit can scale to that one, return the multiplier needed to convert
    // If conversion not possible, return empty.
    public Optional<Rational> canScaleTo(Unit unit, UnitManager mgr) throws UserException
    {
        Pair<Rational, Unit> thisCanon = mgr.canonicalise(this);
        Pair<Rational, Unit> otherCanon = mgr.canonicalise(unit);

        if (!thisCanon.getSecond().units.equals(otherCanon.getSecond().units))
            return Optional.empty();
        else
            return Optional.of(thisCanon.getFirst().divides(otherCanon.getFirst()));
    }

    public Map<SingleUnit, Integer> getDetails()
    {
        return Collections.unmodifiableMap(units);
    }

    public static Unit _test_make(Object... params)
    {
        Unit u = new Unit();
        for (int i = 0; i < params.length; i += 2)
        {
            SingleUnit unit = (SingleUnit)params[i];
            if (u.units.containsKey(unit))
            {
                throw new RuntimeException("Duplicate unit: " + unit);
            }
            u.units.put(unit, (Integer)params[i+1]);
        }
        return u;
    }
}
