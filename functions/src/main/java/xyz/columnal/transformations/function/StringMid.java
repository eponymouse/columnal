package xyz.columnal.transformations.function;

import annotation.qual.Value;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.utility.SimulationFunction;
import xyz.columnal.transformations.expression.function.ValueFunction;

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
        public @Value Object _call() throws UserException, InternalException
        {
            String src = arg(0, String.class);
            int codePointStart = intArg(1);
            if (codePointStart <= 0)
                throw new UserException("Invalid count when calling middle function: " + codePointStart);
            int codePointCount = intArg(2);
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
