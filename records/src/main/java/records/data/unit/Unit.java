package records.data.unit;

import org.sosy_lab.common.rationals.Rational;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.UnitLexer;
import records.grammar.UnitParser;
import records.grammar.UnitParser.SingleOrScaleContext;
import records.grammar.UnitParser.UnitContext;
import utility.Utility;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Created by neil on 09/12/2016.
 */
public class Unit
{
    // 1 if empty.  Maps single unit to power (can be negative, can't be zero)
    private final Map<SingleUnit, Integer> units = new HashMap<>();
    private final Rational scale;

    public static class SingleUnit
    {
        private final String unit;
        private final String description;
        private final String prefix;
        private final String suffix;

        // package-private
        SingleUnit(String unit, String description, String prefix, String suffix)
        {
            this.unit = unit;
            this.description = description;
            this.prefix = prefix;
            this.suffix = suffix;
        }
    }

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
        Unit u = new Unit(scale.times(rhs.scale));
        u.units.putAll(units);
        for (Entry<SingleUnit, Integer> rhsUnit : rhs.units.entrySet())
        {
            u.units.merge(rhsUnit.getKey(), rhsUnit.getValue(), (l, r) -> (l - r == 0) ? null : l - r);
        }
        return u;
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
                return only.getKey().prefix;
        }
        return "";
    }

}
