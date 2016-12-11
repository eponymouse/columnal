package records.data.unit;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.common.rationals.Rational;
import records.error.UserException;
import utility.Utility;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.StringJoiner;
import java.util.TreeMap;

/**
 * Created by neil on 09/12/2016.
 */
public class Unit
{
    // 1 if empty.  Maps single unit to power (can be negative, can't be zero)
    private final TreeMap<SingleUnit, Integer> units = new TreeMap<>();
    private final Rational scale;

    public Unit(SingleUnit singleUnit)
    {
        units.put(singleUnit, 1);
        scale = Rational.ONE;
    }

    public Unit(Rational scale)
    {
        this.scale = scale;
    }

    private Unit()
    {
        scale = Rational.ONE;
    }

    public static Unit SCALAR = new Unit();


    @SuppressWarnings("nullness")
    public Unit times(Unit rhs)
    {
        Unit u = new Unit(scale.times(rhs.scale));
        u.units.putAll(units);
        for (Entry<SingleUnit, Integer> rhsUnit : rhs.units.entrySet())
        {
            u.units.merge(rhsUnit.getKey(), rhsUnit.getValue(), (l, r) -> (l + r == 0) ? null : l + r);
        }
        return u;
    }

    @SuppressWarnings("nullness")
    public Unit divide(Unit rhs)
    {
        return times(rhs.reciprocal());
    }


    public Unit raisedTo(int power) throws UserException
    {
        if (power == 0)
            throw new UserException("Invalid raise to power zero");
        Unit u = new Unit(Utility.rationalToPower(scale, power));
        u.units.putAll(units);
        u.units.replaceAll((s, origPower) -> origPower * power);
        return u;
    }

    public String getDisplayPrefix()
    {
        if (scale.equals(Rational.ONE) && units.size() == 1)
        {
            Entry<SingleUnit, Integer> only = units.entrySet().iterator().next();
            if (only.getValue() == 1)
                return only.getKey().getPrefix();
        }
        return "";
    }

    @Override
    public String toString()
    {
        StringJoiner top = new StringJoiner(" ");
        // First add positives:
        int[] pos = new int[] {0};
        units.forEach((u, p) -> {
            if (p >= 1)
            {
                if (p > 1)
                    top.add(u.getName() + "^" + p);
                else
                    top.add(u.getName());
                pos[0] += 1;
            }
        });
        int neg = units.size() - pos[0];
        String allUnits = top.toString();
        if (neg > 0)
        {
            StringJoiner bottom = new StringJoiner(" ");

            units.forEach((u, p) ->
            {
                if (p < 0)
                    bottom.add(u.getName());
                if (p < -1)
                    bottom.add(u.getName() + "^" + (-p));
            });
            if (neg > 1)
                allUnits += "/(" + bottom + ")";
            else
                allUnits += "/" + bottom;

        }
        if (!allUnits.isEmpty() && scale.equals(Rational.ONE))
            return allUnits;
        else
            return scale.toString() + allUnits;
    }

    public String forDisplay()
    {
        if (units.isEmpty() && scale.equals(Rational.ONE))
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

        if (!units.equals(unit.units)) return false;
        return scale.equals(unit.scale);
    }

    @Override
    public int hashCode()
    {
        int result = units.hashCode();
        result = 31 * result + scale.hashCode();
        return result;
    }

    public Unit reciprocal()
    {
        Unit u = new Unit(scale.reciprocal());
        for (Entry<SingleUnit, Integer> entry : units.entrySet())
        {
            u.units.put(entry.getKey(), - entry.getValue());
        }
        return u;
    }
}
