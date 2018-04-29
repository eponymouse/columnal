package records.transformations.function;

import annotation.qual.Value;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;
import utility.SimulationFunction;
import utility.Utility;
import utility.Utility.ListEx;
import utility.ValueFunction;

public class Max extends FunctionDefinition
{
    public Max() throws InternalException
    {
        super("comparison:maximum");
    }

    @Override
    public ValueFunction getInstance(SimulationFunction<String, DataType> paramTypes)
    {
        return new Instance();
    }

    private static class Instance extends ValueFunction
    {
        @Override
        public @Value Object call(@Value Object param) throws UserException, InternalException
        {
            @Value ListEx list = Utility.cast(param, ListEx.class);
            if (list.size() == 0)
            {
                throw new UserException("Cannot take minimum of empty list");
            }
            else
            {
                @Value Object max = list.get(0);
                for (int i = 1; i < list.size(); i++)
                {
                    @Value Object val = list.get(i);
                    if (Utility.compareValues(max, val) < 0)
                        max = val;
                }
                return max;
            }
        }
    }
}
