package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.ExpressionSaver;
import records.gui.expressioneditor.GeneralExpressionEntry;
import records.gui.expressioneditor.GeneralExpressionEntry.Keyword;
import records.typeExp.TypeExp;
import styled.StyledString;
import threadchecker.OnThread;
import utility.Pair;
import utility.StreamTreeBuilder;
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
    public @Nullable CheckedExp check(ColumnLookup dataLookup, TypeState state, LocationInfo locationInfo, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        @Nullable CheckedExp conditionType = condition.check(dataLookup, state, LocationInfo.UNIT_DEFAULT, onError);
        if (conditionType == null)
            return null;
        if (conditionType.expressionKind == ExpressionKind.PATTERN)
        {
            onError.recordError(condition, StyledString.s("Condition cannot be a pattern"));
            return null;
        }
        if (onError.recordError(this, TypeExp.unifyTypes(TypeExp.bool(this), conditionType.typeExp)) == null)
        {
            return null;
        }
        @Nullable CheckedExp thenType = thenExpression.check(dataLookup, conditionType.typeState, LocationInfo.UNIT_DEFAULT, onError);
        @Nullable CheckedExp elseType = elseExpression.check(dataLookup, conditionType.typeState, LocationInfo.UNIT_DEFAULT, onError);
        if (thenType == null || elseType == null)
            return null;

        if (thenType.expressionKind == ExpressionKind.PATTERN)
        {
            onError.recordError(thenExpression, StyledString.s("Cannot have a pattern directly inside an if"));
            return null;
        }
        if (elseType.expressionKind == ExpressionKind.PATTERN)
        {
            onError.recordError(elseExpression, StyledString.s("Cannot have a pattern directly inside an if"));
            return null;
        }

        return onError.recordTypeAndError(this, TypeExp.unifyTypes(thenType.typeExp, elseType.typeExp), ExpressionKind.EXPRESSION, state);
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
            return new ValueResult(thenResult.value, state, ImmutableList.of(condValState, thenResult));
        }
        else
        {
            // Else gets original state, condition didn't pass:
            ValueResult elseResult = elseExpression.calculateValue(state);
            return new ValueResult(elseResult.value, state, ImmutableList.of(condValState, elseResult));
        }
    }

    @Override
    public Stream<ColumnReference> allColumnReferences()
    {
        return Stream.of(condition, thenExpression, elseExpression).flatMap(Expression::allColumnReferences);
    }

    @Override
    public Stream<String> allVariableReferences()
    {
        return Stream.of(condition, thenExpression, elseExpression).flatMap(Expression::allVariableReferences);
    }

    @Override
    public String save(boolean structured, BracketedStatus surround, TableAndColumnRenames renames)
    {
        String content = "@if " + condition.save(structured, BracketedStatus.MISC, renames) + " @then " + thenExpression.save(structured, BracketedStatus.MISC,renames) + " @else " + elseExpression.save(structured, BracketedStatus.MISC, renames) + " @endif";
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
    public Stream<SingleLoader<Expression, ExpressionSaver>> loadAsConsecutive(BracketedStatus bracketedStatus)
    {
        StreamTreeBuilder<SingleLoader<Expression, ExpressionSaver>> r = new StreamTreeBuilder<>();
        r.add(GeneralExpressionEntry.load(Keyword.IF));
        r.addAll(condition.loadAsConsecutive(BracketedStatus.MISC));
        r.add(GeneralExpressionEntry.load(Keyword.THEN));
        r.addAll(thenExpression.loadAsConsecutive(BracketedStatus.MISC));
        r.add(GeneralExpressionEntry.load(Keyword.ELSE));
        r.addAll(elseExpression.loadAsConsecutive(BracketedStatus.MISC));
        r.add(GeneralExpressionEntry.load(Keyword.ENDIF));
        return r.stream();
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
}
