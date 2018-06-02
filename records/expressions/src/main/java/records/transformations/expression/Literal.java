package records.transformations.expression;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.ConsecutiveBase.BracketedStatus;
import records.gui.expressioneditor.ExpressionNodeParent;
import records.gui.expressioneditor.GeneralExpressionEntry;
import records.gui.expressioneditor.GeneralExpressionEntry.Lit;
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
    public final @Nullable CheckedExp check(TableLookup dataLookup, TypeState typeState, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        return onError.recordTypeAndError(this, checkType(typeState), ExpressionKind.EXPRESSION, typeState);
    }

    protected abstract Either<StyledString, TypeExp> checkType(TypeState typeState) throws InternalException;

    @Override
    public Stream<ColumnReference> allColumnReferences()
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

    @Override
    public Stream<SingleLoader<Expression, ExpressionNodeParent>> loadAsConsecutive(BracketedStatus bracketedStatus)
    {
        return Stream.of(GeneralExpressionEntry.load(new Lit(this)));
    }

    public abstract String editString();
}
