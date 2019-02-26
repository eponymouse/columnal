package records.transformations.function.datetime;

import annotation.qual.Value;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.function.FunctionDefinition;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.SimulationFunction;
import records.transformations.expression.function.ValueFunction;

import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;

public class SecondsBetween extends FunctionDefinition
{
    public SecondsBetween() throws InternalException
    {
        super("datetime:seconds between");
    }

    @Override
    public @OnThread(Tag.Simulation) ValueFunction getInstance(TypeManager typeManager, SimulationFunction<String, Either<Unit, DataType>> paramTypes) throws InternalException, UserException
    {
        return new ValueFunction()
        {
            @Override
            public @OnThread(Tag.Simulation) @Value Object _call() throws InternalException, UserException
            {
                @Value Temporal lhs = arg(0, Temporal.class);
                @Value Temporal rhs = arg(1, Temporal.class);
                return DataTypeUtility.value(ChronoUnit.SECONDS.between(lhs, rhs));
            }
        };
    }
}
