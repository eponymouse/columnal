package records.transformations.function.core;

import annotation.funcdoc.qual.FuncDocKey;
import annotation.qual.Value;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import records.transformations.function.FunctionDefinition;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Either;
import xyz.columnal.utility.SimulationFunction;
import records.transformations.expression.function.ValueFunction;

public class AsType extends FunctionDefinition
{

    public static final @FuncDocKey String NAME = "core:as type";

    public AsType() throws InternalException
    {
        super(NAME);
    }

    @Override
    public ValueFunction getInstance(TypeManager typeManager, SimulationFunction<String, Either<Unit, DataType>> paramTypes)
    {
        return new Instance();
    }

    private static class Instance extends ValueFunction
    {
        @Override
        public @OnThread(Tag.Simulation) @Value Object _call() throws InternalException, UserException
        {
            return arg(1);
        }
    }
}
