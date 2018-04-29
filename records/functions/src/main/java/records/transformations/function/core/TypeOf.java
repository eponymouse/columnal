package records.transformations.function.core;

import annotation.qual.Value;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.function.FunctionDefinition;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.SimulationFunction;
import utility.TaggedValue;
import utility.ValueFunction;

public class TypeOf extends FunctionDefinition
{
    public TypeOf() throws InternalException
    {
        super("core:typeOf");
    }

    @Override
    public ValueFunction getInstance(SimulationFunction<String, DataType> paramTypes)
    {
        return new Instance();
    }

    private static class Instance extends ValueFunction
    {
        @Override
        public @OnThread(Tag.Simulation) @Value Object call(@Value Object arg) throws InternalException, UserException
        {
            // TODO return the actual type literal once we define the GADT
            return new TaggedValue(0, null);
        }
    }
}
