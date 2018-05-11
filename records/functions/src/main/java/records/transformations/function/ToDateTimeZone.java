package records.transformations.function;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.SimulationFunction;
import utility.Utility;
import utility.ValueFunction;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by neil on 16/12/2016.
 */
public class ToDateTimeZone extends ToTemporalFunction
{
    private static List<List<DateTimeFormatter>> FORMATS = new ArrayList<>();

    @Override
    public ImmutableList<FunctionDefinition> getTemporalFunctions(UnitManager mgr) throws InternalException
    {
        ImmutableList.Builder<FunctionDefinition> r = ImmutableList.builder();
        //r.add(fromString("datetimezoned from string"));
        /* TODO
        r.add(new FunctionDefinition("datetimezoned.from.datetime.zone", "datetimezoned.from.datetime.zone.mini", DT_Z::new, DataType.date(getResultType()),
            DataType.tuple(DataType.date(new DateTimeInfo(DateTimeType.DATETIME)), DataType.TEXT)));
        */
        r.add(new FunctionDefinition("datetime:datetimezoned") {
            @Override
            public @OnThread(Tag.Simulation) ValueFunction getInstance(SimulationFunction<String, DataType> paramTypes) throws InternalException, UserException
            {
                return new D_T_Z();
            }
        });
        /*
        r.add(new FunctionDefinition("datetimezoned.from.date.timezoned", "datetimezoned.from.date.timezoned.mini", D_TZ::new, DataType.date(getResultType()), DataType.tuple(
            DataType.date(new DateTimeInfo(DateTimeType.YEARMONTHDAY)),
            DataType.date(new DateTimeInfo(DateTimeType.TIMEOFDAYZONED)))));
            */
        return r.build();
    }

    @Override
    DateTimeInfo getResultType()
    {
        return new DateTimeInfo(DateTimeType.DATETIMEZONED);
    }

    @Override
    @Value Temporal fromTemporal(TemporalAccessor temporalAccessor)
    {
        return ZonedDateTime.from(temporalAccessor);
    }

    private class D_TZ extends ValueFunction
    {
        @Override
        public @Value Object call(@Value Object simpleParams) throws UserException, InternalException
        {
            @Value Object[] paramList = Utility.valueTuple(simpleParams, 2);
            OffsetTime t = (OffsetTime)paramList[1];
            return ZonedDateTime.of((LocalDate)paramList[0], t.toLocalTime(), t.getOffset());
        }
    }

    private class DT_Z extends ValueFunction
    {
        @Override
        public @Value Object call(@Value Object simpleParams) throws UserException, InternalException
        {
            @Value Object[] paramList = Utility.valueTuple(simpleParams, 2);
            return ZonedDateTime.of((LocalDateTime)paramList[0], ZoneId.of((String)paramList[1]));
        }
    }

    private class D_T_Z extends ValueFunction
    {
        @Override
        public @Value Object call(@Value Object simpleParams) throws UserException, InternalException
        {
            @Value Object[] paramList = Utility.valueTuple(simpleParams, 3);
            return ZonedDateTime.of(LocalDateTime.of((LocalDate) paramList[0], (LocalTime)paramList[1]), ZoneId.of((String) paramList[2]));
        }
    }
}
