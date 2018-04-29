package records.transformations.function.list;

import annotation.qual.Value;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.function.FunctionDefinition;
import utility.SimulationFunction;
import utility.Utility;
import utility.Utility.ListEx;
import utility.ValueFunction;

public class Single extends FunctionDefinition
{
    public Single() throws InternalException
    {
        super("list:single");
    }

    @Override
    public ValueFunction getInstance(SimulationFunction<String, DataType> paramTypes) throws InternalException
    {
        return new Instance();
    }

    private static class Instance extends ValueFunction
    {
        @Override
        public @Value Object call(@Value Object param) throws UserException, InternalException
        {
            ListEx list = Utility.cast(param, ListEx.class);
            if (list.size() == 1)
                return list.get(0);
            else
                throw new UserException("List must be of size 1, but was size " + list.size());
        }
    }
}
