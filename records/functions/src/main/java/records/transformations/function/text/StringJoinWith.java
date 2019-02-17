package records.transformations.function.text;

import annotation.qual.Value;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.function.FunctionDefinition;
import utility.Either;
import utility.SimulationFunction;
import utility.Utility;
import utility.Utility.ListEx;
import records.data.ValueFunction;

public class StringJoinWith extends FunctionDefinition
{
    public StringJoinWith() throws InternalException
    {
        super("text:join text with");
    }

    @Override
    public ValueFunction getInstance(TypeManager typeManager, SimulationFunction<String, Either<Unit, DataType>> paramTypes)
    {
        return new Instance();
    }

    private static class Instance extends ValueFunction
    {
        @Override
        public @Value Object call() throws UserException, InternalException
        {
            @Value ListEx textList = arg(0, ListEx.class);
            String separator = arg(1, String.class);
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < textList.size(); i++)
            {
                if (i > 0)
                    b.append(separator);
                b.append(Utility.cast(textList.get(i), String.class));
            }
            return DataTypeUtility.value(b.toString());
        }
    }
}
