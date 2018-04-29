package records.transformations.function.core;

import annotation.qual.Value;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.function.FunctionDefinition;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.SimulationFunction;
import utility.Utility;
import utility.ValueFunction;

public class AsType extends FunctionDefinition
{
    public AsType() throws InternalException
    {
        super("core:asType");
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
            return Utility.castTuple(arg, 2)[1];
        }
    }
}
