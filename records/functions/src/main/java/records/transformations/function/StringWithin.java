package records.transformations.function;

import annotation.qual.Value;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.error.UserException;
import utility.SimulationFunction;
import utility.Utility;
import records.data.ValueFunction;

public class StringWithin //extends FunctionDefinition
{
    public StringWithin() throws InternalException
    {
        //super("text:within");
    }

    //@Override
    public ValueFunction getInstance(SimulationFunction<String, DataType> paramTypes)
    {
        return new Instance();
    }

    private static class Instance extends ValueFunction
    {
        @Override
        public @Value Object call() throws UserException, InternalException
        {
            return DataTypeUtility.value(arg(1, String.class).contains(arg(0, String.class)));
        }
    }
}
