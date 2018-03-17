package records.transformations.expression;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.ColumnId;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.ExpressionNodeParent;
import records.gui.expressioneditor.GeneralExpressionEntry;
import records.gui.expressioneditor.GeneralExpressionEntry.Status;
import records.gui.expressioneditor.OperandNode;
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
    public SingleLoader<Expression, ExpressionNodeParent, OperandNode<Expression, ExpressionNodeParent>> loadAsSingle()
    {
        return (p, s) -> new GeneralExpressionEntry(editString(), false, Status.LITERAL, p, s);
    }

    protected abstract String editString();
}
