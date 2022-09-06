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
import records.transformations.expression.function.ValueFunction;

public class StringJoin extends FunctionDefinition
{
    public StringJoin() throws InternalException
    {
        super("text:join text");
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
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < textList.size(); i++)
            {
                b.append(Utility.cast(textList.get(i), String.class));
            }
            return DataTypeUtility.value(b.toString());
        }
    }
}
