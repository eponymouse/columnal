package records.transformations.function;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UnimplementedException;
import records.error.UserException;
import records.transformations.expression.Expression;
import records.transformations.expression.Expression._test_TypeVary;
import utility.ExConsumer;
import utility.Pair;

import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Created by neil on 14/01/2017.
 */
public class Count extends FunctionDefinition
{
    public Count()
    {
        super("count");
    }

    @Override
    public @Nullable Pair<FunctionInstance, DataType> typeCheck(List<Unit> units, List<DataType> params, ExConsumer<String> onError, UnitManager mgr) throws InternalException, UserException
    {
        throw new UnimplementedException();
    }

    @Override
    public Pair<List<Unit>, List<Expression>> _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws UserException, InternalException
    {
        return new Pair<>(Collections.emptyList(), Collections.singletonList(newExpressionOfDifferentType.getDifferentType(DataType.array())));
    }
}
