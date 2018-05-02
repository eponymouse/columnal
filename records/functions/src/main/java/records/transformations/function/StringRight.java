package records.transformations.function;

import annotation.qual.Value;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.error.UserException;
import utility.SimulationFunction;
import utility.Utility;
import utility.ValueFunction;

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
        public @Value Object call(@Value Object param) throws UserException, InternalException
        {
            @Value Object @Value [] params = Utility.castTuple(param, 2);
            @Value String src = Utility.cast(params[0], String.class);
            int codePointCount = Utility.cast(params[1], Integer.class);
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
