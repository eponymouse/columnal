package records.transformations.function.text;

import annotation.qual.Value;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.function.FunctionDefinition;
import utility.SimulationFunction;
import utility.Utility;
import utility.Utility.ListEx;
import utility.ValueFunction;

public class StringJoin extends FunctionDefinition
{
    public StringJoin() throws InternalException
    {
        super("text:join text");
    }

    @Override
    public ValueFunction getInstance(SimulationFunction<String, DataType> paramTypes)
    {
        return new Instance();
    }

    private static class Instance extends ValueFunction
    {
        @Override
        public @Value Object call(@Value Object param) throws UserException, InternalException
        {
            @Value ListEx textList = Utility.cast(param, ListEx.class);
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < textList.size(); i++)
            {
                b.append(Utility.cast(textList.get(i), String.class));
            }
            return b.toString();
        }
    }
}
