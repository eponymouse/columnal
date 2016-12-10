package records.transformations.expression;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.ColumnId;
import records.data.datatype.DataType;
import records.error.FunctionInt;
import records.error.InternalException;
import records.error.UserException;
import utility.Pair;

import java.util.Random;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Created by neil on 27/11/2016.
 */
public abstract class Literal extends Expression
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
    public @Nullable Expression _test_typeFailure(Random r, FunctionInt<@Nullable DataType, Expression> newExpressionOfDifferentType) throws InternalException, UserException
    {
        return null;
    }
}
