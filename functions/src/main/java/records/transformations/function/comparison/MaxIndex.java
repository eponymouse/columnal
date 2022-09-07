package records.transformations.function.comparison;

import annotation.qual.Value;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import records.transformations.expression.function.ValueFunction;
import records.transformations.function.FunctionDefinition;
import xyz.columnal.utility.Either;
import xyz.columnal.utility.SimulationFunction;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Utility.ListEx;

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
