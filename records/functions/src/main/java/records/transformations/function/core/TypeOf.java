package records.transformations.function.core;

import annotation.qual.Value;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.function.FunctionDefinition;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.SimulationFunction;
import utility.TaggedValue;
import records.data.ValueFunction;

public class TypeOf extends FunctionDefinition
{
    public TypeOf() throws InternalException
    {
        super("core:typeOf");
    }

    @Override
    public ValueFunction getInstance(TypeManager typeManager, SimulationFunction<String, Either<Unit, DataType>> paramTypes)
    {
        return new Instance();
    }

    private static class Instance extends ValueFunction
    {
        @Override
        public @OnThread(Tag.Simulation) @Value Object call() throws InternalException, UserException
        {
            // TODO return the actual type literal once we define the GADT
            return new TaggedValue(0, null);
        }
    }
}
