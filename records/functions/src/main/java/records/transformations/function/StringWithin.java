package records.transformations.function;

import annotation.qual.Value;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.error.UserException;
import utility.Utility;
import utility.ValueFunction;

public class StringWithin extends FunctionDefinition
{
    public StringWithin()
    {
        super("within", "within.mini", Instance::new, DataType.BOOLEAN, DataType.tuple(DataType.TEXT, DataType.TEXT));
    }

    private static class Instance extends ValueFunction
    {

        @Override
        public @Value Object call(@Value Object param) throws UserException, InternalException
        {
            @Value Object[] params = Utility.castTuple(param, 2);
            return DataTypeUtility.value(Utility.cast(params[1], String.class).contains(Utility.cast(params[0], String.class)));
        }
    }
}
