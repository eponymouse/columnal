package records.transformations.function;

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
 * Created by neil on 14/01/2017.
 */
public class Count extends FunctionDefinition
{
    public Count()
    {
        super("count", "count.short");
    }

    @Override
    public List<FunctionType> getOverloads(UnitManager mgr) throws InternalException
    {
        return Collections.singletonList(new FunctionType(Instance::new, DataType.NUMBER, DataType.array(), null));
    }

    @Override
    public Pair<List<Unit>, Expression> _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws UserException, InternalException
    {
        return new Pair<>(Collections.emptyList(), newExpressionOfDifferentType.getDifferentType(DataType.array()));
    }

    private static class Instance extends FunctionInstance
    {
        @Override
        public @OnThread(Tag.Simulation) @Value Object getValue(int rowIndex, @Value Object param) throws UserException, InternalException
        {
            return Utility.value(Utility.valueList(param).size());
        }
    }
}
