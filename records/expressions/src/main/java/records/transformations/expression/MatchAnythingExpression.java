package records.transformations.expression;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.datatype.DataTypeUtility;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.explanation.Explanation.ExecutionType;
import records.transformations.expression.visitor.ExpressionVisitor;
import records.typeExp.MutVar;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;

import java.util.Random;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Created by neil on 23/02/2017.
 */
public class MatchAnythingExpression extends NonOperatorExpression
{
    @Override
    public @Nullable CheckedExp check(ColumnLookup dataLookup, TypeState state, ExpressionKind kind, LocationInfo locationInfo, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        if (kind != ExpressionKind.PATTERN)
        {
            onError.recordError(this, StyledString.s("_ is not valid outside a pattern"));
            return null;
        }
        
        return new CheckedExp(onError.recordTypeNN(this, new MutVar(this)), state);
    }

    @Override
    public ValueResult calculateValue(EvaluateState state) throws UserException, InternalException
    {
        throw new InternalException("Calling getValue on \"_\" pattern (should only call matchAsPattern)");
    }

    @Override
    public @OnThread(Tag.Simulation) ValueResult matchAsPattern(@Value Object value, EvaluateState state) throws InternalException, UserException
    {
        // Like the name says, we match anything:
        return explanation(DataTypeUtility.value(true), ExecutionType.MATCH, state, ImmutableList.of(), ImmutableList.of(), false);
    }

    @Override
    public boolean hideFromExplanation(boolean skipIfTrivial)
    {
        return skipIfTrivial;
    }

    @Override
    public String save(boolean structured, BracketedStatus surround, TableAndColumnRenames renames)
    {
        return "_";
    }

    @Override
    protected StyledString toDisplay(BracketedStatus bracketedStatus, ExpressionStyler expressionStyler)
    {
        return expressionStyler.styleExpression(StyledString.s("_"), this);
    }

    @Override
    public Stream<Pair<Expression, Function<Expression, Expression>>> _test_childMutationPoints()
    {
        return Stream.empty();
    }

    @Override
    public @Nullable Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        return null;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        return o instanceof MatchAnythingExpression;
    }

    @Override
    public int hashCode()
    {
        return 0;
    }

    @Override
    public Expression replaceSubExpression(Expression toReplace, Expression replaceWith)
    {
        return this == toReplace ? replaceWith : this;
    }

    @Override
    public <T> T visit(ExpressionVisitor<T> visitor)
    {
        return visitor.matchAnything(this);
    }
}
