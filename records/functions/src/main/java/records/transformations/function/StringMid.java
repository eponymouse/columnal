package records.transformations.function;

import annotation.qual.Value;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.error.UserException;
import utility.SimulationFunction;
import utility.Utility;
import utility.ValueFunction;

public class StringMid //extends FunctionDefinition
{
    public StringMid() throws InternalException
    {
        //super("text:middle");
    }

    //@Override
    public ValueFunction getInstance(SimulationFunction<String, DataType> paramTypes)
    {
        return new Instance();
    }

    private static class Instance extends ValueFunction
    {
        @Override
        public @Value Object call(@Value Object param) throws UserException, InternalException
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
