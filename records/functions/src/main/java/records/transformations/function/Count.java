package records.transformations.function;

import annotation.qual.Value;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
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
        super("count", Instance::new, DataType.NUMBER, DataType.array());
    }

    public static FunctionGroup group()
    {
        return new FunctionGroup("count.short", new Count());
    }
    
    @Override
    public <E> Pair<List<Unit>, E> _test_typeFailure(Random r, _test_TypeVary<E> newExpressionOfDifferentType, UnitManager unitManager) throws UserException, InternalException
    {
        return new Pair<>(Collections.emptyList(), newExpressionOfDifferentType.getDifferentType(DataType.array()));
    }

    private static class Instance extends FunctionInstance
    {
        @Override
        public @OnThread(Tag.Simulation) @Value Object getValue(int rowIndex, @Value Object param) throws UserException, InternalException
        {
            return DataTypeUtility.value(Utility.valueList(param).size());
        }
    }
}
