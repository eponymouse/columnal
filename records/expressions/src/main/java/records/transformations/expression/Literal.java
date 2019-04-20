package records.transformations.expression;

import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.UnitExpression.UnitLookupException;
import records.typeExp.TypeExp;
import styled.StyledString;
import utility.Either;
import utility.Pair;

import java.util.Random;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Created by neil on 27/11/2016.
 */
public abstract class Literal extends NonOperatorExpression
{
    @Override
    public final @Nullable CheckedExp check(ColumnLookup dataLookup, TypeState typeState, LocationInfo locationInfo, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        @Recorded TypeExp typeExp = onError.recordType(this, checkType(typeState, locationInfo, onError));
        if (typeExp == null)
            return null;
        else
            return new CheckedExp(typeExp, typeState, ExpressionKind.EXPRESSION);
    }

    protected abstract @Nullable TypeExp checkType(TypeState typeState, LocationInfo locationInfo, ErrorAndTypeRecorder onError) throws InternalException;

    @Override
    public Stream<ColumnReference> allColumnReferences()
    {
        return Stream.empty();
    }

    @Override
    public Stream<String> allVariableReferences()
    {
        return Stream.empty();
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

    public abstract String editString();

    @Override
    public Expression replaceSubExpression(Expression toReplace, Expression replaceWith)
    {
        return this == toReplace ? replaceWith : this;
    }

    @Override
    public boolean hideFromExplanation(boolean skipIfTrivial)
    {
        return true;
    }
}
