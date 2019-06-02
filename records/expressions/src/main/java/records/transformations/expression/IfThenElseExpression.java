package records.transformations.expression;

import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.visitor.ExpressionVisitor;
import records.typeExp.TypeExp;
import styled.StyledString;
import utility.Pair;
import utility.Utility;

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
    private final @Recorded Expression condition;
    private final @Recorded Expression thenExpression;
    private final @Recorded Expression elseExpression;

    public IfThenElseExpression(@Recorded Expression condition, @Recorded Expression thenExpression, @Recorded Expression elseExpression)
    {
        this.condition = condition;
        this.thenExpression = thenExpression;
        this.elseExpression = elseExpression;
    }


    @Override
    public @Nullable CheckedExp check(ColumnLookup dataLookup, TypeState state, ExpressionKind kind, LocationInfo locationInfo, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        @Nullable CheckedExp conditionType = condition.check(dataLookup, state, ExpressionKind.EXPRESSION, LocationInfo.UNIT_DEFAULT, onError);
        if (conditionType == null)
            return null;
        if (onError.recordError(condition, TypeExp.unifyTypes(TypeExp.bool(this), conditionType.typeExp)) == null)
        {
            return null;
        }
        @Nullable CheckedExp thenType = thenExpression.check(dataLookup, conditionType.typeState, ExpressionKind.EXPRESSION, LocationInfo.UNIT_DEFAULT, onError);
        @Nullable CheckedExp elseType = elseExpression.check(dataLookup, conditionType.typeState, ExpressionKind.EXPRESSION, LocationInfo.UNIT_DEFAULT, onError);
        if (thenType == null || elseType == null)
            return null;

        return onError.recordTypeAndError(this, TypeExp.unifyTypes(thenType.typeExp, elseType.typeExp), state);
    }

    @Override
    public ValueResult calculateValue(EvaluateState state) throws UserException, InternalException
    {
        ValueResult condValState = condition.calculateValue(state);
        Boolean b = Utility.cast(condValState.value, Boolean.class);
        // We always return original state to outermost,
        // but then-branch gets state from condition:
        if (b)
        {
            ValueResult thenResult = thenExpression.calculateValue(condValState.evaluateState);
            return result(thenResult.value, state, ImmutableList.of(condValState, thenResult));
        }
        else
        {
            // Else gets original state, condition didn't pass:
            ValueResult elseResult = elseExpression.calculateValue(state);
            return result(elseResult.value, state, ImmutableList.of(condValState, elseResult));
        }
    }

    @Override
    public String save(boolean structured, BracketedStatus surround, TableAndColumnRenames renames)
    {
        String content = "@if " + condition.save(structured, BracketedStatus.DONT_NEED_BRACKETS, renames) + " @then " + thenExpression.save(structured, BracketedStatus.DONT_NEED_BRACKETS,renames) + " @else " + elseExpression.save(structured, BracketedStatus.DONT_NEED_BRACKETS, renames) + " @endif";
        return content;
    }

    @Override
    public StyledString toDisplay(BracketedStatus surround, ExpressionStyler expressionStyler)
    {
        StyledString content = StyledString.concat(
            StyledString.s("if "),
            condition.toDisplay(BracketedStatus.DONT_NEED_BRACKETS, expressionStyler),
            StyledString.s(" then "),
            thenExpression.toDisplay(BracketedStatus.DONT_NEED_BRACKETS, expressionStyler),
            StyledString.s(" else "),
            elseExpression.toDisplay(BracketedStatus.DONT_NEED_BRACKETS, expressionStyler),
            StyledString.s(" endif")
        );
        return expressionStyler.styleExpression(content, this); //surround != BracketedStatus.NEED_BRACKETS ? content : StyledString.roundBracket(content);
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

    @Override
    @SuppressWarnings("recorded") // Because the replaced version is immediately loaded again
    public Expression replaceSubExpression(Expression toReplace, Expression replaceWith)
    {
        if (this == toReplace)
            return replaceWith;
        else 
            return new IfThenElseExpression(
                condition.replaceSubExpression(toReplace, replaceWith),
                thenExpression.replaceSubExpression(toReplace, replaceWith),
                elseExpression.replaceSubExpression(toReplace, replaceWith)
            );
    }

    @Override
    public <T> T visit(ExpressionVisitor<T> visitor)
    {
        return visitor.ifThenElse(this, condition, thenExpression, elseExpression);
    }
}
