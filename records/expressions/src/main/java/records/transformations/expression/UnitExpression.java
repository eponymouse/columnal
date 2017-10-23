package records.transformations.expression;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.UnitLexer;
import records.grammar.UnitParser;
import records.grammar.UnitParser.SingleContext;
import records.grammar.UnitParser.UnbracketedUnitContext;
import records.grammar.UnitParser.UnitContext;
import records.gui.expressioneditor.ConsecutiveBase;
import records.gui.expressioneditor.OperandNode;
import records.gui.expressioneditor.UnitNodeParent;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Pair;
import utility.Utility;

import java.util.List;
import java.util.stream.Stream;

public abstract class UnitExpression
{
    public static UnitExpression load(String text) throws InternalException, UserException
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
                SingleUnitExpression singleUnit = new SingleUnitExpression(singleItem.singleUnit().getText());
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
            return new UnitTimesExpression(Stream.concat(Stream.of(ctx.unit()), ctx.timesBy().stream().map(t -> t.unit())).map(c -> loadUnit(c)).collect(ImmutableList.toImmutableList()));
        }
        else
        {
            return loadUnit(ctx.unit());
        }
    }

    // Either gives back an error + (maybe empty) list of quick fixes, or a successful unit
    public abstract Either<Pair<String, List<UnitExpression>>, Unit> asUnit(UnitManager unitManager);

    public abstract String save(boolean topLevel);

    @OnThread(Tag.FXPlatform)
    public abstract OperandNode<UnitExpression> edit(ConsecutiveBase<UnitExpression, UnitNodeParent> parent, boolean topLevel);

    @Override
    public abstract int hashCode();

    @Override
    public abstract boolean equals(@Nullable Object obj);
}
