package xyz.columnal.transformations.function;

import annotation.qual.Value;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.utility.SimulationFunction;
import xyz.columnal.transformations.expression.function.ValueFunction;

public class StringLeft //extends FunctionDefinition
{
    public StringLeft() throws InternalException
    {
        //super("text:left");
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
            String src = arg(0, String.class);
            int codePointCount = intArg(1);
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
