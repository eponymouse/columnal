package records.transformations.function.text;

import annotation.qual.Value;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import records.transformations.function.FunctionDefinition;
import xyz.columnal.utility.Either;
import xyz.columnal.utility.SimulationFunction;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Utility.ListEx;
import records.transformations.expression.function.ValueFunction;

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
        public @Value Object _call() throws UserException, InternalException
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
