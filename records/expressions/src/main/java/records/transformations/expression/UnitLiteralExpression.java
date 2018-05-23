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
import records.gui.expressioneditor.OperandNode;
import records.gui.expressioneditor.UnitLiteralNode;
import records.typeExp.TypeExp;
import records.typeExp.units.UnitExp;
import styled.StyledString;
import utility.Either;
import utility.Pair;
import utility.TaggedValue;

import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Stream;

public class UnitLiteralExpression extends NonOperatorExpression
{
    private final UnitExpression unitExpression;

    public UnitLiteralExpression(UnitExpression unitExpression)
    {
        this.unitExpression = unitExpression;
    }

    @Override
    public @Nullable CheckedExp check(TableLookup dataLookup, TypeState typeState, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        // Numeric literals, should not call check on us.
        // Everyone else sees a Unit GADT
        Either<Pair<StyledString, List<UnitExpression>>, UnitExp> saved = unitExpression.asUnit(typeState.getUnitManager());
        return saved.<@Nullable CheckedExp>eitherInt(error -> {onError.recordError(this, error.getFirst()); return null;}, unit -> 
            onError.recordTypeAndError(this, Either.right(TypeExp.unitExpToUnitGADT(this, unit)), ExpressionKind.EXPRESSION, typeState));
    }

    @Override
    public @Value Object getValue(EvaluateState state) throws UserException, InternalException
    {
        // TODO return the actual type literal once we define the GADT
        return new TaggedValue(0, null);
    }

    @Override
    public Stream<ColumnReference> allColumnReferences()
    {
        return Stream.empty();
    }

    @Override
    public String save(BracketedStatus surround, TableAndColumnRenames renames)
    {
        return "{" + unitExpression.save(true) + "}";
    }

    @Override
    public SingleLoader<Expression, ExpressionNodeParent, OperandNode<Expression, ExpressionNodeParent>> loadAsSingle()
    {
        return (p, s) -> new UnitLiteralNode(p, unitExpression);
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
        return StyledString.concat(StyledString.s("{"), unitExpression.toStyledString(), StyledString.s("}"));
    }

    public UnitExpression getUnit()
    {
        return unitExpression;
    }
}
