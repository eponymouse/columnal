package records.transformations.function;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.SimulationFunction;
import records.transformations.expression.function.ValueFunction;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;

/**
 * Created by neil on 16/12/2016.
 */
public class ToDateTime extends ToTemporalFunction
{
    @Override
    public ImmutableList<FunctionDefinition> getTemporalFunctions(UnitManager mgr) throws InternalException
    {
        ImmutableList.Builder<FunctionDefinition> r = ImmutableList.builder();
        /* TODO
        r.add(fromString("datetime.from.string", "datetime.from.string.mini"));
        r.add(new FunctionDefinition("datetime.from.datetimezoned", "datetime.from.datetimezoned.mini", FromTemporalInstance::new, DataType.date(getResultType()), DataType.date(new DateTimeInfo(DateTimeType.DATETIMEZONED))));
        */
        r.add(new FromTemporal("datetime:datetime from datetimezoned"));
        r.add(new FunctionDefinition("datetime:datetime")
        {
            @Override
            public @OnThread(Tag.Simulation) ValueFunction getInstance(TypeManager typeManager, SimulationFunction<String, Either<Unit, DataType>> paramTypes) throws InternalException, UserException
            {
                return new DateAndTimeInstance();
            }
        });
        
        return r.build();
    }

    @Override
    DateTimeInfo getResultType()
    {
        return new DateTimeInfo(DateTimeType.DATETIME);
    }

    @Override
    @Value Temporal fromTemporal(TemporalAccessor temporalAccessor)
    {
        return LocalDateTime.from(temporalAccessor);
    }

    private class DateAndTimeInstance extends ValueFunction
    {
        @Override
        public @Value Object call() throws UserException, InternalException
        {
            return LocalDateTime.of(arg(0, LocalDate.class), arg(1, LocalTime.class));
        }
    }
}
