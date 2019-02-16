package records.transformations.function;

import annotation.qual.Value;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import utility.Either;
import utility.SimulationFunction;
import utility.Utility;
import records.data.ValueFunction;

public class StringReplaceAll extends FunctionDefinition
{
    public StringReplaceAll() throws InternalException
    {
        super("text:replace all");
    }

    @Override
    public ValueFunction getInstance(TypeManager typeManager, SimulationFunction<String, Either<Unit, DataType>> paramTypes)
    {
        return new Instance();
    }

    private static class Instance extends ValueFunction
    {

        @Override
        public @Value Object call(@Value Object param) throws UserException, InternalException
        {
            @Value Object[] params = Utility.castTuple(param, 3);
            @Value String target = Utility.cast(params[0], String.class);
            @Value String whole = Utility.cast(params[2], String.class);
            // Java does act on replacing empty string, but we don't:
            if (target.isEmpty())
                return whole;
            return DataTypeUtility.value(whole.replace(target, Utility.cast(params[1], String.class)));
        }
    }
}
