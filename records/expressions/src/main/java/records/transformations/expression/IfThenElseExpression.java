package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.ConsecutiveBase.BracketedStatus;
import records.gui.expressioneditor.ExpressionNodeParent;
import records.gui.expressioneditor.IfThenElseNode;
import records.gui.expressioneditor.OperandNode;
import records.types.TypeExp;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;

import java.util.Random;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Really, this could be done in a match expression, but I suspect
 * that would confuse users.
 *
 * This *cannot* be done in an if function as originally planned,
 * because functions are eager in their arguments, and you want
 * to be lazy in the unused then/else part for situations like
 * if y <> 0 then x / y else 0
 */
public class IfThenElseExpression extends NonOperatorExpression
{
    private final Expression condition;
    private final Expression thenExpression;
    private final Expression elseExpression;

    public IfThenElseExpression(@Recorded Expression condition, @Recorded Expression thenExpression, @Recorded Expression elseExpression)
    {
        this.condition = condition;
        this.thenExpression = thenExpression;
        this.elseExpression = elseExpression;
    }


    @Override
    public @Nullable @Recorded TypeExp check(TableLookup dataLookup, TypeState state, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        @Nullable TypeExp conditionType = condition.check(dataLookup, state, onError);
        if (conditionType == null)
            return null;
        if (onError.recordError(this, TypeExp.unifyTypes(TypeExp.bool(this), conditionType)) == null)
        {
            return null;
        }
        @Nullable TypeExp thenType = thenExpression.check(dataLookup, state, onError);
        @Nullable TypeExp elseType = elseExpression.check(dataLookup, state, onError);
        if (thenType == null || elseType == null)
            return null;

        return onError.recordTypeAndError(this, TypeExp.unifyTypes(thenType, elseType));
    }

    @Override
    public @OnThread(Tag.Simulation) @Value Object getValue(int rowIndex, EvaluateState state) throws UserException, InternalException
    {
        Boolean b = (Boolean)condition.getValue(rowIndex, state);
        if (b)
            return thenExpression.getValue(rowIndex, state);
        else
            return elseExpression.getValue(rowIndex, state);
    }

    @Override
    public Stream<ColumnReference> allColumnReferences()
    {
        return Stream.of(condition, thenExpression, elseExpression).flatMap(Expression::allColumnReferences);
    }

    @Override
    public String save(BracketedStatus surround, TableAndColumnRenames renames)
    {
        String content = "@if " + condition.save(BracketedStatus.MISC, renames) + " @then " + thenExpression.save(BracketedStatus.MISC,renames) + " @else " + elseExpression.save(BracketedStatus.MISC, renames);
        return surround != BracketedStatus.MISC ? content : ("(" + content + ")");
    }

    @Override
    public StyledString toDisplay(BracketedStatus surround)
    {
        StyledString content = StyledString.concat(
            StyledString.s("if "),
            condition.toDisplay(BracketedStatus.MISC),
            StyledString.s(" then "),
            thenExpression.toDisplay(BracketedStatus.MISC),
            StyledString.s(" else "),
            elseExpression.toDisplay(BracketedStatus.MISC));
        return surround != BracketedStatus.MISC ? content : StyledString.roundBracket(content);
    }

    @Override
    public SingleLoader<Expression, ExpressionNodeParent, OperandNode<Expression, ExpressionNodeParent>> loadAsSingle()
    {
        return (p, s) -> new IfThenElseNode(p, s, condition, thenExpression, elseExpression);
    }

    @Override
    public Stream<Pair<Expression, Function<Expression, Expression>>> _test_childMutationPoints()
    {
        return Stream.empty(); // TODO
    }

    @Override
    public @Nullable Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        return null; // TODO
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        IfThenElseExpression that = (IfThenElseExpression) o;

        if (!condition.equals(that.condition)) return false;
        if (!thenExpression.equals(that.thenExpression)) return false;
        return elseExpression.equals(that.elseExpression);
    }

    @Override
    public int hashCode()
    {
        int result = condition.hashCode();
        result = 31 * result + thenExpression.hashCode();
        result = 31 * result + elseExpression.hashCode();
        return result;
    }

    public Expression _test_getCondition()
    {
        return condition;
    }

    public Expression _test_getThen()
    {
        return thenExpression;
    }

    public Expression _test_getElse()
    {
        return elseExpression;
    }
}
