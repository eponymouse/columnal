package records.transformations.expression;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.ColumnId;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.ConsecutiveBase;
import records.gui.expressioneditor.GeneralEntry;
import records.gui.expressioneditor.GeneralEntry.Status;
import records.gui.expressioneditor.OperandNode;
import utility.FXPlatformFunction;
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
    public Stream<ColumnId> allColumnNames()
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
    public FXPlatformFunction<ConsecutiveBase, OperandNode> loadAsSingle()
    {
        return c -> new GeneralEntry(editString(), Status.LITERAL, c);
    }

    protected abstract String editString();
}
