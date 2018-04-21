package records.transformations.function;

import annotation.qual.Value;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.error.UserException;
import utility.Utility;
import utility.ValueFunction;

public class StringLength extends FunctionDefinition
{
    public StringLength()
    {
        super("text/text length", "length.mini", Instance::new, DataType.NUMBER, DataType.TEXT);
    }
    
    private static class Instance extends ValueFunction
    {

        @Override
        public @Value Object call(@Value Object param) throws UserException, InternalException
        {
            @Value String str = Utility.cast(param, String.class);
            return DataTypeUtility.value(str.codePointCount(0, str.length()));
        }
    }
}
