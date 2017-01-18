package records.transformations.function;

import annotation.userindex.qual.UserIndex;
import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.Expression;
import records.transformations.expression.Expression._test_TypeVary;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExConsumer;
import utility.Pair;
import utility.Utility;

import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Created by neil on 17/01/2017.
 */
public class GetElement extends FunctionDefinition
{
    public GetElement()
    {
        super("element");
    }

    // Takes parameters: column/array, index
    @Override
    public @Nullable Pair<FunctionInstance, DataType> typeCheck(List<Unit> units, List<DataType> params, ExConsumer<String> onError, UnitManager mgr) throws InternalException, UserException
    {
        if (!units.isEmpty())
        {
            onError.accept("Function does not take any units");
            return null;
        }
        if (params.size() != 2)
        {
            onError.accept("Function takes two parameters");
            return null;
        }
        if (!params.get(0).isArray())
        {
            onError.accept("First parameter must be a list");
            return null;
        }
        if (DataType.checkSame(DataType.NUMBER, params.get(1), onError) == null)
        {
            return null;
        }
        List<DataType> arrayType = params.get(0).getMemberType();
        if (arrayType.isEmpty())
        {
            // Must be empty-array literal, reasonable to give error:
            onError.accept("First parameter cannot be the empty list");
        }
        return new Pair<>(new Instance(), arrayType.get(0));
    }

    @Override
    public Pair<List<Unit>, List<Expression>> _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws UserException, InternalException
    {
        // TODO
        return new Pair<>(Collections.emptyList(), Collections.emptyList());
    }

    private static class Instance extends FunctionInstance
    {
        @Override
        @OnThread(Tag.Simulation)
        public @Value Object getValue(int rowIndex, ImmutableList<@Value Object> params) throws UserException, InternalException
        {
            @UserIndex int userIndex = Utility.userIndex(params.get(1));
            return Utility.getAtIndex(Utility.valueList(params.get(0)), userIndex);
        }
    }
}
