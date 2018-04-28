package records.transformations.function;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import utility.Utility;
import utility.ValueFunction;

import java.time.LocalTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by neil on 16/12/2016.
 */
public class ToTimeAndZone extends ToTemporalFunction
{
    private static final List<List<DateTimeFormatter>> FORMATS = new ArrayList<>();

    @Override
    public ImmutableList<FunctionDefinition> getTemporalFunctions(UnitManager mgr) throws InternalException
    {
        ImmutableList.Builder<FunctionDefinition> r = ImmutableList.builder();
        r.add(fromString("timezoned.from.string", "timezoned.from.string.mini"));
        r.add(new FunctionDefinition("timezoned.from.datetimezoned", "timezoned.from.datetimezoned.mini", FromTemporalInstance::new, DataType.date(getResultType()), DataType.date(new DateTimeInfo(DateTimeType.DATETIMEZONED))));
        r.add(new FunctionDefinition("timezoned", "timezoned.mini", T_Z::new, DataType.date(getResultType()),
            DataType.tuple(DataType.date(new DateTimeInfo(DateTimeType.TIMEOFDAY)), DataType.TEXT)));
        return r.build();
    }

    @Override
    DateTimeInfo getResultType()
    {
        return new DateTimeInfo(DateTimeType.TIMEOFDAYZONED);
    }

    @Override
    @Value Temporal fromTemporal(TemporalAccessor temporalAccessor)
    {
        return OffsetTime.from(temporalAccessor);
    }

    private class T_Z extends ValueFunction
    {
        @Override
        public @Value Object call(@Value Object simpleParams) throws UserException, InternalException
        {
            @Value Object[] paramList = Utility.valueTuple(simpleParams, 2);
            return OffsetTime.of((LocalTime)paramList[0], ZoneOffset.of((String)paramList[1]));
        }
    }
}
