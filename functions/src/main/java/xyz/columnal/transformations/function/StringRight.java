package xyz.columnal.transformations.function;

import annotation.qual.Value;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.utility.SimulationFunction;
import xyz.columnal.transformations.expression.function.ValueFunction;

public class StringRight //extends FunctionDefinition
{
    public StringRight() throws InternalException
    {
        //super("text:right");
    }

    //@Override
    public ValueFunction getInstance(SimulationFunction<String, DataType> paramTypes) throws InternalException
    {
        return new Instance();
    }

    private static class Instance extends ValueFunction
    {

        @Override
        public @Value Object _call() throws UserException, InternalException
        {
            @Value String src = arg(0, String.class);
            int codePointCount = intArg(1);
            if (codePointCount < 0)
                throw new UserException("Invalid count when calling right function: " + codePointCount);
            try
            {
                int totalCodepoints = src.codePointCount(0, src.length());
                return DataTypeUtility.value(src.substring(src.offsetByCodePoints(0, Math.max(0, totalCodepoints - codePointCount))));
            }
            catch (IndexOutOfBoundsException e)
            {
                // Just return whole string:
                return src;
            }
        }
    }
}
