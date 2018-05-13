package records.jellytype;

import com.google.common.collect.ImmutableMap;
import com.sun.xml.internal.bind.v2.util.QNameMap;
import org.checkerframework.checker.nullness.qual.KeyFor;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.unit.SingleUnit;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.UnitLexer;
import records.grammar.UnitParser;
import records.grammar.UnitParser.SingleContext;
import records.grammar.UnitParser.SingleUnitContext;
import records.grammar.UnitParser.TimesByContext;
import records.grammar.UnitParser.UnbracketedUnitContext;
import records.grammar.UnitParser.UnitContext;
import records.loadsave.OutputBuilder;
import records.typeExp.MutVar;
import records.typeExp.TypeExp;
import records.typeExp.units.MutUnitVar;
import records.typeExp.units.UnitExp;
import utility.ComparableEither;
import utility.Either;
import utility.Utility;

import java.util.Map.Entry;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class JellyUnit
{
    // scalar if empty.  Maps single unit variable or unit to power (can be negative, can't be zero)
    private final TreeMap<ComparableEither<String, SingleUnit>, Integer> units = new TreeMap<>();
    
    // package-visible
    private JellyUnit()
    {
    }

    public static JellyUnit fromConcrete(Unit unit)
    {
        JellyUnit u = new JellyUnit();
        u.units.putAll(unit.getDetails().entrySet().stream().collect(Collectors.<Entry<SingleUnit, Integer>, ComparableEither<String, SingleUnit>, Integer>toMap(
            (Entry<SingleUnit, Integer> e) -> ComparableEither.right(e.getKey()),
            e -> e.getValue()    
        )));
        return u;
    }

    public UnitExp makeUnitExp(ImmutableMap<String, Either<MutUnitVar, MutVar>> typeVariables) throws InternalException
    {
        UnitExp u = UnitExp.SCALAR;
        for (Entry<@KeyFor("units") ComparableEither<String, SingleUnit>, Integer> e : units.entrySet())
        {
            u = u.times(e.getKey().eitherInt(name -> new UnitExp(typeVariables.get(name).getLeft("Variable " + name + " should be type variable but was unit variable")),
                (SingleUnit single) -> new UnitExp(single).raisedTo(e.getValue())));
        }
        return u;
    }

    public Unit makeUnit(ImmutableMap<String, Either<Unit, DataType>> typeVariables) throws InternalException
    {
        Unit u = Unit.SCALAR;
        for (Entry<@KeyFor("units") ComparableEither<String, SingleUnit>, Integer> e : units.entrySet())
        {
            u = u.times(e.getKey().eitherInt(name -> {throw new InternalException("Cannot instantiate unit with variable " + name);},
                (SingleUnit single) -> new Unit(single).raisedTo(e.getValue())));
        }
        return u;
    }

    protected JellyUnit divideBy(JellyUnit rhs)
    {
        return times(raiseBy(-1));
    }
    
    protected JellyUnit times(JellyUnit rhs)
    {
        JellyUnit u = new JellyUnit();
        u.units.putAll(units);
        for (Entry<@KeyFor("rhs.units") ComparableEither<String, SingleUnit>, Integer> rhsUnit : rhs.units.entrySet())
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

    protected JellyUnit raiseBy(int power)
    {
        if (power == 0)
            return new JellyUnit();
        JellyUnit u = new JellyUnit();
        u.units.putAll(units);
        u.units.replaceAll((s, origPower) -> origPower * power);
        return u;
    }
    
    public void save(OutputBuilder output) throws InternalException
    {
        boolean first = true;
        for (Entry<@KeyFor("units") ComparableEither<String, SingleUnit>, Integer> e : units.entrySet())
        {
            if (!first)
            {
                output.raw("*");
            }
            first = false;
            e.getKey().eitherInt(name -> output.t(UnitParser.UNITVAR, UnitParser.VOCABULARY).raw(name),
                u -> output.raw(u.getName()));
            if (e.getValue() != 1)
            {
                output.raw("^" + e.getValue());
            }
        }
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JellyUnit jellyUnit = (JellyUnit) o;
        return Objects.equals(units, jellyUnit.units);
    }

    @Override
    public int hashCode()
    {

        return Objects.hash(units);
    }

    public static JellyUnit unitVariable(String name)
    {
        JellyUnit u = new JellyUnit();
        u.units.put(ComparableEither.left(name), 1);
        return u;
    }

    public static JellyUnit load(String src, UnitManager mgr) throws UserException, InternalException
    {
        return load(Utility.<UnbracketedUnitContext, UnitParser>parseAsOne(src, UnitLexer::new, UnitParser::new, p -> p.unitUse().unbracketedUnit()), mgr);
    }

    public static JellyUnit load(UnbracketedUnitContext ctx, UnitManager mgr) throws InternalException
    {
        JellyUnit u = load(ctx.unit(), mgr);
        if (ctx.divideBy() != null)
            u = u.divideBy(load(ctx.divideBy().unit(), mgr));
        else
        {
            for (TimesByContext rhs : ctx.timesBy())
            {
                u = u.times(load(rhs.unit(), mgr));
            }
        }

        return u;
    }

    private static JellyUnit load(UnitContext ctx, UnitManager mgr) throws InternalException
    {
        if (ctx.unbracketedUnit() != null)
        {
            return load(ctx.unbracketedUnit(), mgr);
        }
        else
        {
            SingleContext singleItem = ctx.single();
            if (singleItem.singleUnit() == null && singleItem.NUMBER() != null)
            {
                return new JellyUnit();
            }
            else
            {
                JellyUnit singleUnit = load(singleItem.singleUnit(), mgr);
                if (ctx.single().NUMBER() != null)
                {
                    try
                    {
                        return singleUnit.raiseBy(Integer.parseInt(ctx.single().NUMBER().getText()));
                    }
                    catch (NumberFormatException e)
                    {
                        // Best default:
                        return singleUnit;
                    }
                }
                else
                {
                    return singleUnit;
                }
            }
        }
    }

    private static JellyUnit load(SingleUnitContext ctx, UnitManager mgr) throws InternalException
    {
        JellyUnit u = new JellyUnit();
        if (ctx.UNITVAR() != null)
        {
            u.units.put(ComparableEither.left(ctx.IDENT().getText()), 1);
        }
        else
        {
            u.units.put(ComparableEither.right(mgr.getDeclared(ctx.IDENT().getText())), 1);
        }
        return u;
    }

    public TreeMap<ComparableEither<String, SingleUnit>, Integer> getDetails()
    {
        return units;
    }
}
