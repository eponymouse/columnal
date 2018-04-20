package records.transformations.function;

import annotation.qual.Value;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.error.UserException;
import utility.Utility;
import utility.Utility.ListEx;
import utility.ValueFunction;

import java.util.ArrayList;
import java.util.List;

public class StringWithinIndex extends FunctionDefinition
{
    public StringWithinIndex()
    {
        super("within.positions", "within.positions.mini", Instance::new, DataType.array(DataType.NUMBER), DataType.tuple(DataType.TEXT, DataType.TEXT));
    }

    private static class Instance extends ValueFunction
    {

        @Override
        public @Value Object call(@Value Object param) throws UserException, InternalException
        {
            @Value Object[] params = Utility.castTuple(param, 2);
            @Value String big = Utility.cast(params[1], String.class);
            @Value String small = Utility.cast(params[0], String.class);
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
