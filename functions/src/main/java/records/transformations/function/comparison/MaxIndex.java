package records.transformations.function.comparison;

import annotation.qual.Value;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.function.ValueFunction;
import records.transformations.function.FunctionDefinition;
import utility.Either;
import utility.SimulationFunction;
import utility.Utility;
import utility.Utility.ListEx;

public class MaxIndex extends FunctionDefinition
{
    public static final String NAME = "maximum index";
    
    public MaxIndex() throws InternalException
    {
        super("comparison:maximum index");
    }

    @Override
    public ValueFunction getInstance(TypeManager typeManager, SimulationFunction<String, Either<Unit, DataType>> paramTypes) throws InternalException
    {
        return new Instance();
    }

    private static class Instance extends ValueFunction
    {
        @Override
        public @Value Object _call() throws UserException, InternalException
        {
            @Value ListEx list = arg(0, ListEx.class);
            if (list.size() == 0)
            {
                throw new UserException("Cannot take maximum of empty list");
            }
            else
            {
                @Value Object max = list.get(0);
                int zeroIndexMax = 0;
                for (int i = 1; i < list.size(); i++)
                {
                    @Value Object val = list.get(i);
                    if (Utility.compareValues(max, val) < 0)
                    {
                        max = val;
                        zeroIndexMax = i;
                    }
                }
                // Add one to make it one-based:
                return DataTypeUtility.value(zeroIndexMax + 1);
            }
        }
    }
}
