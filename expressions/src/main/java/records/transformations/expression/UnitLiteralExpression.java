package records.transformations.expression;

import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.TableAndColumnRenames;
import xyz.columnal.data.datatype.DataType.TagType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.UnitManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.jellytype.JellyUnit;
import records.transformations.expression.UnitExpression.UnitLookupException;
import records.transformations.expression.visitor.ExpressionVisitor;
import xyz.columnal.typeExp.TypeExp;
import xyz.columnal.styled.StyledString;
import xyz.columnal.utility.Either;
import xyz.columnal.utility.Pair;
import xyz.columnal.utility.TaggedValue;

import java.util.Objects;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * An Expression that contains a UnitExpression.
 */
public class UnitLiteralExpression extends NonOperatorExpression
{
    private final @Recorded UnitExpression unitExpression;

    public UnitLiteralExpression(@Recorded UnitExpression unitExpression)
    {
        this.unitExpression = unitExpression;
    }

    @Override
    public @Nullable CheckedExp check(@Recorded UnitLiteralExpression this, ColumnLookup dataLookup, TypeState typeState, ExpressionKind kind, LocationInfo locationInfo, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        // Numeric literals, should not call check on us.
        // Everyone else sees a Unit GADT
        try
        {
            JellyUnit saved = unitExpression.asUnit(typeState.getUnitManager());
            return onError.recordTypeAndError(this, Either.right(TypeExp.unitExpToUnitGADT(this, saved.makeUnitExp(ImmutableMap.of()))), typeState);
        }
        catch (UnitLookupException e)
        {
            if (e.errorMessage != null)
                onError.recordError(this, e.errorMessage);
            return null;
        }
    }

    @Override
    public ValueResult calculateValue(EvaluateState state)
    {
        // TODO return the actual type literal once we define the GADT
        return result(new TaggedValue(0, null, DataTypeUtility.fromTags(ImmutableList.<TagType<Object>>of(new TagType<Object>("Type", null)))), state);
    }

    @Override
    public String save(SaveDestination saveDestination, BracketedStatus surround, TableAndColumnRenames renames)
    {
        return "unit{" + unitExpression.save(saveDestination, true) + "}";
    }

    @Override
    public Stream<Pair<Expression, Function<Expression, Expression>>> _test_childMutationPoints()
    {
        return Stream.of();
    }

    @Override
    public @Nullable Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        return null;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UnitLiteralExpression that = (UnitLiteralExpression) o;
        return Objects.equals(unitExpression, that.unitExpression);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(unitExpression);
    }

    @Override
    protected StyledString toDisplay(DisplayType displayType, BracketedStatus bracketedStatus, ExpressionStyler expressionStyler)
    {
        return expressionStyler.styleExpression(StyledString.concat(StyledString.s("unit{"), unitExpression.toStyledString(), StyledString.s("}")), this);
    }

    public @Recorded UnitExpression getUnit()
    {
        return unitExpression;
    }

    @Override
    public Expression replaceSubExpression(Expression toReplace, Expression replaceWith)
    {
        return this == toReplace ? replaceWith : this;
    }

    @Override
    public <T> T visit(ExpressionVisitor<T> visitor)
    {
        return visitor.litUnit(this, unitExpression);
    }
}
