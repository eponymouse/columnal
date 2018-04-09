package records.transformations.function;

import annotation.qual.Value;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

public class StringLength extends FunctionDefinition
{
    public StringLength()
    {
        super("text length", "length.mini", Instance::new, DataType.NUMBER, DataType.TEXT);
    }
    
    public static FunctionGroup group()
    {
        return new FunctionGroup("length.short", new StringLength());
    }
    
    private static class Instance extends FunctionInstance
    {

        @Override
        public @OnThread(Tag.Simulation) @Value Object getValue(int rowIndex, @Value Object param) throws UserException, InternalException
        {
            @Value String str = Utility.cast(param, String.class);
            return DataTypeUtility.value(str.codePointCount(0, str.length()));
        }
    }
}
