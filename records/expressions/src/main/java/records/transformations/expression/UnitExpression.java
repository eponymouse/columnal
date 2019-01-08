package records.transformations.expression;

import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.KeyFor;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.unit.SingleUnit;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.UnitLexer;
import records.grammar.UnitParser;
import records.grammar.UnitParser.SingleContext;
import records.grammar.UnitParser.UnbracketedUnitContext;
import records.grammar.UnitParser.UnitContext;
import records.gui.expressioneditor.UnitSaver;
import records.jellytype.JellyUnit;
import records.typeExp.units.UnitExp;
import styled.StyledShowable;
import styled.StyledString;
import utility.ComparableEither;
import utility.Either;
import utility.IdentifierUtility;
import utility.Pair;
import utility.Utility;

import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Stream;

public abstract class UnitExpression implements LoadableExpression<UnitExpression, UnitSaver>, StyledShowable, Replaceable<UnitExpression>
{
    @SuppressWarnings("recorded")
    public static @Recorded UnitExpression load(Unit unit)
    {
        ImmutableList<UnitExpression> top = unit.getDetails().entrySet().stream()
            .filter((Entry<@KeyFor("unit.getDetails()") SingleUnit, Integer> p) -> p.getValue() > 0)
            .<UnitExpression>map((Entry<@KeyFor("unit.getDetails()") SingleUnit, Integer> p) -> {
                SingleUnitExpression single = new SingleUnitExpression(p.getKey().getName());
                return p.getValue().intValue() == 1 ? single : new UnitRaiseExpression(single, p.getValue().intValue());
            }).collect(ImmutableList.<UnitExpression>toImmutableList());
        
        UnitExpression r = top.isEmpty() ? new UnitExpressionIntLiteral(1) : (top.size() == 1 ? top.get(0) : new UnitTimesExpression(top));
        
        ImmutableList<UnitExpression> bottom = unit.getDetails().entrySet().stream()
            .filter((Entry<@KeyFor("unit.getDetails()")SingleUnit, Integer> p) -> p.getValue() < 0)
            .<UnitExpression>map((Entry<@KeyFor("unit.getDetails()")SingleUnit, Integer> p) -> {
                SingleUnitExpression single = new SingleUnitExpression(p.getKey().getName());
                return p.getValue().intValue() == -1 ? single : new UnitRaiseExpression(single, - p.getValue().intValue());
            }).collect(ImmutableList.<UnitExpression>toImmutableList());
        
        if (bottom.isEmpty())
            return r;
        else if (bottom.size() == 1)
            return new UnitDivideExpression(r, bottom.get(0));
        else
            return new UnitDivideExpression(r, new UnitTimesExpression(bottom));
    }

    @SuppressWarnings("recorded") // Don't record when loading from Jelly
    public static @Recorded UnitExpression load(JellyUnit unit)
    {
        ImmutableList<UnitExpression> top = unit.getDetails().entrySet().stream()
            .filter((Entry<@KeyFor("unit.getDetails()") ComparableEither<String, SingleUnit>, Integer> p) -> p.getValue() > 0)
            .<UnitExpression>map((Entry<@KeyFor("unit.getDetails()") ComparableEither<String, SingleUnit>, Integer> p) -> {
                UnitExpression single = p.getKey().either(n -> InvalidSingleUnitExpression.identOrUnfinished(n), u -> new SingleUnitExpression(u.getName()));
                return p.getValue().intValue() == 1 ? single : new UnitRaiseExpression(single, p.getValue().intValue());
            }).collect(ImmutableList.<UnitExpression>toImmutableList());

        UnitExpression r = top.isEmpty() ? new UnitExpressionIntLiteral(1) : (top.size() == 1 ? top.get(0) : new UnitTimesExpression(top));

        ImmutableList<UnitExpression> bottom = unit.getDetails().entrySet().stream()
            .filter((Entry<@KeyFor("unit.getDetails()")ComparableEither<String, SingleUnit>, Integer> p) -> p.getValue() < 0)
            .<UnitExpression>map((Entry<@KeyFor("unit.getDetails()")ComparableEither<String, SingleUnit>, Integer> p) -> {
                UnitExpression single = p.getKey().either(n -> InvalidSingleUnitExpression.identOrUnfinished(n), u -> new SingleUnitExpression(u.getName()));
                return p.getValue().intValue() == -1 ? single : new UnitRaiseExpression(single, - p.getValue().intValue());
            }).collect(ImmutableList.<UnitExpression>toImmutableList());

        if (bottom.isEmpty())
            return r;
        else if (bottom.size() == 1)
            return new UnitDivideExpression(r, bottom.get(0));
        else
            return new UnitDivideExpression(r, new UnitTimesExpression(bottom));
    }
    
    // We mark as Recorded because constructors require
    // that, even though it's not actually reached GUI yet:
    @SuppressWarnings("recorded")
    public static @Recorded UnitExpression load(String text) throws InternalException, UserException
    {
        return loadUnbracketed(Utility.<UnbracketedUnitContext, UnitParser>parseAsOne(text, UnitLexer::new, UnitParser::new, p -> p.unitUse().unbracketedUnit()));
    }

    private static UnitExpression loadUnit(UnitContext ctx)
    {
        if (ctx.unbracketedUnit() != null)
        {
            return loadUnbracketed(ctx.unbracketedUnit());
        }
        else
        {
            SingleContext singleItem = ctx.single();
            if (singleItem.singleUnit() == null && singleItem.NUMBER() != null)
            {
                try
                {
                    return new UnitExpressionIntLiteral(Integer.parseInt(singleItem.NUMBER().getText()));
                }
                catch (NumberFormatException e)
                {
                    // Zero is guaranteed to be an error, so best default:
                    return new UnitExpressionIntLiteral(0);
                }
            }
            else
            {
                // TODO we need to support unit variables here.
                SingleUnitExpression singleUnit = new SingleUnitExpression(IdentifierUtility.fromParsed(singleItem.singleUnit()));
                if (ctx.single().NUMBER() != null)
                {
                    try
                    {
                        return new UnitRaiseExpression(singleUnit, Integer.parseInt(ctx.single().NUMBER().getText()));
                    }
                    catch (NumberFormatException e)
                    {
                        // Zero is guaranteed to be an error, so best default:
                        return new UnitRaiseExpression(singleUnit, 0);
                    }
                }
                else
                {
                    return singleUnit;
                }
            }
        }
    }

    private static UnitExpression loadUnbracketed(UnbracketedUnitContext ctx)
    {
        if (ctx.divideBy() != null)
        {
            return new UnitDivideExpression(loadUnit(ctx.unit()), loadUnit(ctx.divideBy().unit()));
        }
        else if (ctx.timesBy() != null && ctx.timesBy().size() > 0)
        {
            return new UnitTimesExpression(Stream.<UnitContext>concat(Stream.<UnitContext>of(ctx.unit()), ctx.timesBy().stream().<UnitContext>map(t -> t.unit())).<UnitExpression>map(c -> loadUnit(c)).collect(ImmutableList.<UnitExpression>toImmutableList()));
        }
        else
        {
            return loadUnit(ctx.unit());
        }
    }

    // Either gives back an error + (maybe empty) list of quick fixes, or a successful unit
    public abstract Either<Pair<StyledString, List<UnitExpression>>, JellyUnit> asUnit(UnitManager unitManager);
    
    public abstract String save(boolean structured, boolean topLevel);

    @Override
    public final StyledString toStyledString()
    {
        return StyledString.s(save(true, true));
    }

    @Override
    public abstract int hashCode();

    @Override
    public abstract boolean equals(@Nullable Object obj);

    public abstract boolean isEmpty();

    /**
     * Is this the integer literal 1 ?
     */
    public abstract boolean isScalar();

    @Override
    public String toString()
    {
        return save(true, true);
    }
}
