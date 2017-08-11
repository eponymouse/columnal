package records.transformations.expression;

import com.google.common.collect.ImmutableList;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.UnitLexer;
import records.grammar.UnitParser;
import records.grammar.UnitParser.UnbracketedUnitContext;
import records.grammar.UnitParser.UnitContext;
import records.gui.expressioneditor.ConsecutiveBase;
import records.gui.expressioneditor.ExpressionNodeParent;
import records.gui.expressioneditor.OperandNode;
import records.transformations.expression.UnitTimesExpression.Op;
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
            SingleUnitExpression singleUnit = new SingleUnitExpression(ctx.single().singleUnit().getText());
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

    private static UnitExpression loadUnbracketed(UnbracketedUnitContext ctx)
    {
        if (ctx.divideBy() != null)
        {
            return new UnitDivideExpression(loadUnit(ctx.unit()), loadUnit(ctx.divideBy().unit()));
        }
        else
        {
            return new UnitTimesExpression(Stream.concat(Stream.of(ctx.unit()), ctx.timesBy().stream().map(t -> t.unit())).map(c -> loadUnit(c)).collect(ImmutableList.toImmutableList()),
                ctx.timesBy().stream().map(t -> t.TIMES() != null ? Op.STAR : Op.SPACE).collect(ImmutableList.toImmutableList()));
        }
    }

    // Either gives back an error + (maybe empty) list of quick fixes, or a successful unit
    public abstract Either<Pair<String, List<UnitExpression>>, Unit> asUnit(UnitManager unitManager);

    public abstract String save(boolean topLevel);

    @OnThread(Tag.FXPlatform)
    public abstract OperandNode<UnitExpression> edit(ConsecutiveBase<UnitExpression, ExpressionNodeParent> parent, boolean topLevel);
}
