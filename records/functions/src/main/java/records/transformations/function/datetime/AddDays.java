package records.transformations.function.datetime;

import annotation.qual.Value;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
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

import java.time.DateTimeException;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalUnit;

public class AddDays extends FunctionDefinition
{
    public AddDays() throws InternalException
    {
        super("datetime:add days");
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
                @Value Number rhs = Utility.cast(args[1], Number.class);
                if (!Utility.isIntegral(rhs))
                    throw new UserException("Cannot add " + rhs.toString() + " days; number must be an integer (whole number)");
                try
                {
                    return DataTypeUtility.value(new DateTimeInfo(DateTimeType.YEARMONTHDAY), lhs.plus(rhs.longValue(), ChronoUnit.DAYS));
                }
                catch (ArithmeticException | DateTimeException e)
                {
                    throw new UserException("Overflow while trying to add " + rhs.longValue() + " days to " + lhs.toString());
                }
            }
        };
    }
}
