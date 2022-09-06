package records.transformations.function.text;

import annotation.qual.Value;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import records.transformations.function.FunctionDefinition;
import xyz.columnal.utility.Either;
import xyz.columnal.utility.SimulationFunction;
import records.transformations.expression.function.ValueFunction;

public class StringReplace extends FunctionDefinition
{
    public StringReplace() throws InternalException
    {
        super("text:replace");
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
            @Value String target = arg(0, String.class);
            @Value String whole = arg(2, String.class);
            // Java does act on replacing empty string, but we don't:
            if (target.isEmpty())
                return whole;
            return DataTypeUtility.value(whole.replace(target, arg(1, String.class)));
        }
    }
}
