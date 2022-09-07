package xyz.columnal.transformations.function;

import annotation.qual.Value;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.utility.SimulationFunction;
import xyz.columnal.utility.Utility.ListEx;
import xyz.columnal.transformations.expression.function.ValueFunction;

import java.util.ArrayList;
import java.util.List;

public class StringWithinIndex //extends FunctionDefinition
{
    public StringWithinIndex() throws InternalException
    {
        //super("text:positions within");
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
            @Value String big = arg(1, String.class);
            @Value String small = arg(0, String.class);
            return DataTypeUtility.value(new ListEx()
            {
                private final List<Integer> charIndexes = new ArrayList<>();
                private final List<Integer> codepointIndexes = new ArrayList<>();

                @Override
                public int size() throws InternalException, UserException
                {
                    while (calcNext())
                    {
                    }
                    return charIndexes.size();
                }

                private boolean calcNext()
                {
                    int nextChar = big.indexOf(small, charIndexes.isEmpty() ? 0 : charIndexes.get(charIndexes.size() - 1) + 1);
                    if (nextChar == -1)
                        return false;

                    if (codepointIndexes.isEmpty())
                        codepointIndexes.add(big.codePointCount(0, nextChar));
                    else
                        codepointIndexes.add(codepointIndexes.get(codepointIndexes.size() - 1) + big.codePointCount(charIndexes.get(charIndexes.size() - 1 ), nextChar));
                    charIndexes.add(nextChar);
                    return true;
                }

                @Override
                public @Value Object get(int index) throws InternalException, UserException
                {
                    while (index < codepointIndexes.size() && calcNext())
                    {
                    }
                    return DataTypeUtility.value(codepointIndexes.get(index));
                }
            });
        }
    }
}
