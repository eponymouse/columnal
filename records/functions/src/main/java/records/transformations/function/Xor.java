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
import records.transformations.expression.function.ValueFunction;

public class Xor extends FunctionDefinition
{
    public Xor() throws InternalException
    {
        super("boolean:xor");
    }

    @Override
    public ValueFunction getInstance(TypeManager typeManager, SimulationFunction<String, Either<Unit, DataType>> paramTypes) throws InternalException
    {
        return new Instance();
    }

    private static class Instance extends ValueFunction
    {
        @Override
        public @Value Object call() throws UserException, InternalException
        {
            return DataTypeUtility.value(arg(0, Boolean.class) ^ arg(1, Boolean.class));
        }
    }
}
