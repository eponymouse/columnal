package records.transformations.function;

import annotation.qual.Value;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.error.UserException;
import utility.SimulationFunction;
import utility.Utility;
import utility.ValueFunction;

public class StringReplaceAll extends FunctionDefinition
{
    public StringReplaceAll() throws InternalException
    {
        super("text:replace all");
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
            @Value Object[] params = Utility.castTuple(param, 3);
            return DataTypeUtility.value(Utility.cast(params[2], String.class).replace(Utility.cast(params[0], String.class), Utility.cast(params[1], String.class)));
        }
    }
}
