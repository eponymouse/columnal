package records.transformations.function;

import annotation.qual.Value;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

public class StringLeft extends FunctionDefinition
{
    public StringLeft()
    {
        super("left", "left.mini", Instance::new, DataType.TEXT, DataType.tuple(DataType.TEXT, DataType.NUMBER));
    }
    
    public static FunctionGroup group()
    {
        return new FunctionGroup("left.short", new StringLeft());
    }

    private static class Instance extends FunctionInstance
    {

        @Override
        public @OnThread(Tag.Simulation) @Value Object getValue(int rowIndex, @Value Object param) throws UserException, InternalException
        {
            @Value Object @Value [] params = Utility.castTuple(param, 2);
            String src = Utility.cast(params[0], String.class);
            int codePointCount = Utility.cast(params[1], Integer.class);
            if (codePointCount < 0)
                throw new UserException("Invalid count when calling left function: " + codePointCount);
            try
            {
                return DataTypeUtility.value(src.substring(0, src.offsetByCodePoints(0, codePointCount)));
            }
            catch (IndexOutOfBoundsException e)
            {
                // Just return whole string:
                return DataTypeUtility.value(src);
            }
        }
    }
}
