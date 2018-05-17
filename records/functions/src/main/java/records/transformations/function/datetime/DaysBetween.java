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
import utility.Utility;
import utility.ValueFunction;

import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;

public class DaysBetween extends FunctionDefinition
{
    public DaysBetween() throws InternalException
    {
        super("datetime:days between");
    }

    @Override
    public @OnThread(Tag.Simulation) ValueFunction getInstance(TypeManager typeManager, SimulationFunction<String, Either<Unit, DataType>> paramTypes) throws InternalException, UserException
    {
        return new ValueFunction()
        {
            @Override
            public @OnThread(Tag.Simulation) @Value Object call(@Value Object arg) throws InternalException, UserException
            {
                @Value Object[] args = Utility.castTuple(arg, 2);
                @Value Temporal lhs = Utility.cast(args[0], Temporal.class);
                @Value Temporal rhs = Utility.cast(args[1], Temporal.class);
                return DataTypeUtility.value(ChronoUnit.DAYS.between(lhs, rhs));
            }
        };
    }
}
