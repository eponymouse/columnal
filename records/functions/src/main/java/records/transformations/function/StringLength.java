package records.transformations.function;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

import java.util.List;

public class StringLength extends FunctionType
{
    public StringLength()
    {
        super("length", Instance::new, DataType.NUMBER, DataType.TEXT);
    }
    
    public static FunctionGroup group()
    {
        return new FunctionGroup("length", "length.short", new StringLength());
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
