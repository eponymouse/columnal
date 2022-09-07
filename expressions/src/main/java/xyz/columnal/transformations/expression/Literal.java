package xyz.columnal.transformations.expression;

import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.unit.UnitManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.expression.UnitExpression.UnitLookupException;
import xyz.columnal.typeExp.TypeExp;
import xyz.columnal.styled.StyledString;
import xyz.columnal.utility.Either;
import xyz.columnal.utility.Pair;

import java.util.Random;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Created by neil on 27/11/2016.
 */
public abstract class Literal extends NonOperatorExpression
{
    @Override
    public final @Nullable CheckedExp check(ColumnLookup dataLookup, TypeState typeState, ExpressionKind kind, LocationInfo locationInfo, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        @Recorded TypeExp typeExp = onError.recordType(this, checkType(typeState, locationInfo, onError));
        if (typeExp == null)
            return null;
        else
            return new CheckedExp(typeExp, typeState);
    }

    protected abstract @Nullable TypeExp checkType(TypeState typeState, LocationInfo locationInfo, ErrorAndTypeRecorder onError) throws InternalException;

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
