package records.transformations.function;

import annotation.qual.Value;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import utility.Utility;

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

    public ToTimeAndZone()
    {
        super("timez", "timez.short");
    }

    @Override
    public List<FunctionType> getOverloads(UnitManager mgr) throws InternalException
    {
        ArrayList<FunctionType> r = new ArrayList<>(fromString("timez.string"));
        r.add(new FunctionType(FromTemporalInstance::new, DataType.date(getResultType()), DataType.date(new DateTimeInfo(DateTimeType.DATETIMEZONED)), "timez.datetimez"));
        r.add(new FunctionType(T_Z::new, DataType.date(getResultType()),
            DataType.tuple(DataType.date(new DateTimeInfo(DateTimeType.TIMEOFDAY)), DataType.TEXT), "timez.time_string"));
        return r;
    }

    @Override
    DateTimeInfo getResultType()
    {
        return new DateTimeInfo(DateTimeType.TIMEOFDAYZONED);
    }

    @Override
    protected List<List<DateTimeFormatter>> getFormats()
    {
        if (FORMATS.isEmpty())
        {
            for (DateTimeFormatter dateTimeFormat : ToTime.FORMATS)
            {
                DateTimeFormatterBuilder b = new DateTimeFormatterBuilder().append(dateTimeFormat);
                b.optionalStart().appendLiteral(" ").optionalEnd();
                b.appendZoneId();
                FORMATS.add(Collections.singletonList(b.toFormatter()));
                b = new DateTimeFormatterBuilder().append(dateTimeFormat);
                b.optionalStart().appendLiteral(" ").optionalEnd();
                b.appendOffsetId();
                FORMATS.add(Collections.singletonList(b.toFormatter()));
                b = new DateTimeFormatterBuilder().append(dateTimeFormat);
                b.optionalStart().appendLiteral(" ").optionalEnd();
                b.appendOffsetId().appendLiteral("[").appendZoneRegionId().appendLiteral("]");
                FORMATS.add(Collections.singletonList(b.toFormatter()));
            }
        }
        return FORMATS;
    }

    @Override
    @Value Temporal fromTemporal(TemporalAccessor temporalAccessor)
    {
        return OffsetTime.from(temporalAccessor);
    }

    private class T_Z extends FunctionInstance
    {
        @Override
        public @Value Object getValue(int rowIndex, @Value Object simpleParams) throws UserException, InternalException
        {
            @Value Object[] paramList = Utility.valueTuple(simpleParams, 2);
            return OffsetTime.of((LocalTime)paramList[0], ZoneOffset.of((String)paramList[1]));
        }
    }
}
