package records.transformations.function;

import annotation.qual.Value;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.error.UserException;
import utility.Utility;
import utility.ValueFunction;

public class Not extends FunctionDefinition
{
    public Not()
    {
        super("boolean/not", "not.mini", Instance::new, DataType.BOOLEAN, DataType.BOOLEAN);
    }

    private static class Instance extends ValueFunction
    {

        @Override
        public @Value Object call(@Value Object param) throws UserException, InternalException
        {
            return DataTypeUtility.value(!Utility.cast(param, Boolean.class));
        }
    }
}
