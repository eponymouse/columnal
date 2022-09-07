package xyz.columnal.jellytype;

import annotation.identifier.qual.UnitIdentifier;
import com.google.common.collect.ImmutableMap;
import org.checkerframework.checker.nullness.qual.KeyFor;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.unit.SingleUnit;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.data.unit.UnitManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import records.grammar.UnitLexer;
import records.grammar.UnitParser;
import records.grammar.UnitParser.SingleContext;
import records.grammar.UnitParser.SingleUnitContext;
import records.grammar.UnitParser.TimesByContext;
import records.grammar.UnitParser.UnbracketedUnitContext;
import records.grammar.UnitParser.UnitContext;
import xyz.columnal.loadsave.OutputBuilder;
import xyz.columnal.typeExp.MutVar;
import xyz.columnal.typeExp.TypeExp;
import xyz.columnal.typeExp.units.MutUnitVar;
import xyz.columnal.typeExp.units.UnitExp;
import xyz.columnal.utility.ComparableEither;
import xyz.columnal.utility.Either;
import xyz.columnal.utility.IdentifierUtility;
import xyz.columnal.utility.Utility;

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
            (Entry<SingleUnit, Integer> e) -> ComparableEither.<String, SingleUnit>right(e.getKey()),
            e -> e.getValue()    
        )));
        return u;
    }

    public UnitExp makeUnitExp(ImmutableMap<String, Either<MutUnitVar, MutVar>> typeVariables) throws InternalException
    {
        UnitExp u = UnitExp.SCALAR;
        for (Entry<@KeyFor("units") ComparableEither<String, SingleUnit>, Integer> e : units.entrySet())
        {
            u = u.times(e.getKey().eitherInt(name -> {
                Either<MutUnitVar, MutVar> var = typeVariables.get(name);
                if (var == null)
                    throw new InternalException("Type variable " + name + " not found");
                return new UnitExp(var.getLeft("Variable " + name + " should be type variable but was unit variable"));
                    },
                (SingleUnit single) -> new UnitExp(single).raisedTo(e.getValue())));
        }
        return u;
    }

    public Unit makeUnit(ImmutableMap<String, Either<Unit, DataType>> typeVariables) throws InternalException
    {
        Unit u = Unit.SCALAR;
        for (Entry<@KeyFor("units") ComparableEither<String, SingleUnit>, Integer> e : units.entrySet())
        {
            u = u.times(e.getKey().eitherInt(name -> {
                    Either<Unit, DataType> subst = typeVariables.get(name);
                    if (subst == null)
                        throw new InternalException("Cannot instantiate unit with variable " + name);
                    
                    return subst.eitherInt(unit -> unit, t -> {
                        throw new InternalException("Looked for unit variable " + name + " but found type variable with that name instead.");
                    });
                    
                },
                (SingleUnit single) -> new Unit(single).raisedTo(e.getValue())));
        }
        return u;
    }

    public JellyUnit divideBy(JellyUnit rhs)
    {
        return times(rhs.raiseBy(-1));
    }
    
    public JellyUnit times(JellyUnit rhs)
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

    public JellyUnit raiseBy(int power)
    {
        if (power == 0)
            return new JellyUnit();
        JellyUnit u = new JellyUnit();
        u.units.putAll(units);
        u.units.replaceAll((s, origPower) -> origPower * power);
        return u;
    }
    
    public void save(OutputBuilder output)
    {
        if (units.isEmpty())
        {
            output.raw("1");
            return;
        }
        
        boolean first = true;
        for (Entry<@KeyFor("units") ComparableEither<String, SingleUnit>, Integer> e : units.entrySet())
        {
            if (!first)
            {
                output.raw("*");
            }
            first = false;
            e.getKey().either(name -> output.t(UnitParser.UNITVAR, UnitParser.VOCABULARY).raw(name),
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
        @UnitIdentifier String unitName = IdentifierUtility.fromParsed(ctx);
        if (ctx.UNITVAR() != null)
        {
            u.units.put(ComparableEither.left(unitName), 1);
        }
        else
        {
            u.units.put(ComparableEither.right(mgr.getDeclared(unitName)), 1);
        }
        return u;
    }

    public TreeMap<ComparableEither<String, SingleUnit>, Integer> getDetails()
    {
        return units;
    }

    public boolean isScalar()
    {
        return units.isEmpty();
    }

    // For debugging
    @Override
    public String toString()
    {
        return "JellyUnit{" +
                "units=" + units +
                '}';
    }
}
