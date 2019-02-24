package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableMap;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.ExpressionSaver;
import records.gui.expressioneditor.UnitLiteralExpressionNode;
import records.jellytype.JellyUnit;
import records.typeExp.TypeExp;
import styled.StyledString;
import utility.Either;
import utility.Pair;
import utility.TaggedValue;

import java.util.List;
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
    public @Nullable CheckedExp check(ColumnLookup dataLookup, TypeState typeState, LocationInfo locationInfo, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        // Numeric literals, should not call check on us.
        // Everyone else sees a Unit GADT
        Either<Pair<StyledString, List<UnitExpression>>, JellyUnit> saved = unitExpression.asUnit(typeState.getUnitManager());
        return saved.<@Nullable CheckedExp>eitherInt(error -> {onError.recordError(this, error.getFirst()); return null;}, unit -> 
            onError.recordTypeAndError(this, Either.right(TypeExp.unitExpToUnitGADT(this, unit.makeUnitExp(ImmutableMap.of()))), ExpressionKind.EXPRESSION, typeState));
    }

    @Override
    public ValueResult calculateValue(EvaluateState state) throws UserException, InternalException
    {
        // TODO return the actual type literal once we define the GADT
        return new ValueResult(new TaggedValue(0, null));
    }

    @Override
    public Stream<ColumnReference> allColumnReferences()
    {
        return Stream.empty();
    }

    @Override
    public String save(boolean structured, BracketedStatus surround, TableAndColumnRenames renames)
    {
        return "unit{" + unitExpression.save(structured, true) + "}";
    }

    @Override
    public Stream<SingleLoader<Expression, ExpressionSaver>> loadAsConsecutive(BracketedStatus bracketedStatus)
    {
        return Stream.of(p -> new UnitLiteralExpressionNode(p, unitExpression));
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
    protected StyledString toDisplay(BracketedStatus bracketedStatus)
    {
        return StyledString.concat(StyledString.s("unit{"), unitExpression.toStyledString(), StyledString.s("}"));
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
}
