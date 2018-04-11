package records.transformations.function;

import annotation.qual.Value;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.error.UserException;
import utility.Utility;
import utility.ValueFunction;

public class StringMid extends FunctionDefinition
{
    public StringMid()
    {
        super("middle", "middle.mini", Instance::new, DataType.TEXT, DataType.tuple(DataType.TEXT, DataType.NUMBER, DataType.NUMBER));
    }

    public static FunctionGroup group()
    {
        return new FunctionGroup("middle.short", new StringMid());
    }

    private static class Instance extends ValueFunction
    {
        @Override
        public Object call(@Value Object param) throws UserException, InternalException
        {
            @Value Object @Value [] params = Utility.castTuple(param, 3);
            String src = Utility.cast(params[0], String.class);
            int codePointStart = Utility.cast(params[1], Integer.class);
            if (codePointStart <= 0)
                throw new UserException("Invalid count when calling middle function: " + codePointStart);
            int codePointCount = Utility.cast(params[2], Integer.class);
            if (codePointCount < 0)
                throw new UserException("Invalid count when calling middle function: " + codePointCount);
            try
            {
                // Start is one-based, so subtract one:
                int startIndex = src.offsetByCodePoints(0, codePointStart - 1);
                try
                {
                    return DataTypeUtility.value(src.substring(startIndex, src.offsetByCodePoints(startIndex, codePointCount)));
                }
                catch (IndexOutOfBoundsException e)
                {
                    // Failed to find end, just return all chars from startIndex:
                    return DataTypeUtility.value(src.substring(startIndex));
                }
            }
            catch (IndexOutOfBoundsException e)
            {
                // Searching for start index failed, so just return empty string:
                return DataTypeUtility.value("");
            }
        }
    }
}
