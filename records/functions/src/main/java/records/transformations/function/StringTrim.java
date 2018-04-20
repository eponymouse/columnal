package records.transformations.function;

import annotation.qual.Value;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.error.UserException;
import utility.Utility;
import utility.ValueFunction;

public class StringTrim extends FunctionDefinition
{
    public StringTrim()
    {
        super("trim", "trim.mini", Instance::new, DataType.TEXT, DataType.TEXT);
    }

    private static class Instance extends ValueFunction
    {

        @Override
        public @Value Object call(@Value Object param) throws UserException, InternalException
        {
            String src = Utility.cast(param, String.class);
            // From https://stackoverflow.com/a/28295733
            return DataTypeUtility.value(src.replaceAll("(^(\\h|[\r\n])*)|((\\h|[\n\r])*$)",""));
        }
    }
}
