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
    public ToDateTimeZone()
    {
        super("datetimezoned", "datetimezoned.short");
    }

    private static List<List<DateTimeFormatter>> FORMATS = new ArrayList<>();

    @Override
    public ImmutableList<FunctionDefinition> getTemporalFunctions(UnitManager mgr) throws InternalException
    {
        ImmutableList.Builder<FunctionDefinition> r = ImmutableList.builder();
        r.add(fromString("datetimezoned.from.string"));
        r.add(new FunctionDefinition("datetimezoned.datetime.zone", DT_Z::new, DataType.date(getResultType()),
            DataType.tuple(DataType.date(new DateTimeInfo(DateTimeType.DATETIME)), DataType.TEXT)));
        r.add(new FunctionDefinition("datetimezoned", D_T_Z::new, DataType.date(getResultType()), DataType.tuple(
            DataType.date(new DateTimeInfo(DateTimeType.YEARMONTHDAY)),
            DataType.date(new DateTimeInfo(DateTimeType.TIMEOFDAY)), DataType.TEXT)));
        r.add(new FunctionDefinition("datetimezoned.date.timezoned", D_TZ::new, DataType.date(getResultType()), DataType.tuple(
            DataType.date(new DateTimeInfo(DateTimeType.YEARMONTHDAY)),
            DataType.date(new DateTimeInfo(DateTimeType.TIMEOFDAYZONED)))));
        return r.build();
    }

    @Override
    DateTimeInfo getResultType()
    {
        return new DateTimeInfo(DateTimeType.DATETIMEZONED);
    }

    @Override
    protected List<List<DateTimeFormatter>> getFormats()
    {
        if (FORMATS.isEmpty())
        {
            for (List<DateTimeFormatter> dateTimeFormats : ToDateTime.FORMATS())
            {
                FORMATS.add(Utility.<DateTimeFormatter, DateTimeFormatter>mapList(dateTimeFormats, dateTimeFormat -> {
                    DateTimeFormatterBuilder b = new DateTimeFormatterBuilder().append(dateTimeFormat);
                    b.optionalStart().appendLiteral(" ").optionalEnd();
                    b.appendZoneId();
                    return b.toFormatter();
                }));
                FORMATS.add(Utility.<DateTimeFormatter, DateTimeFormatter>mapList(dateTimeFormats, dateTimeFormat -> {
                    DateTimeFormatterBuilder b = new DateTimeFormatterBuilder().append(dateTimeFormat);
                    b.optionalStart().appendLiteral(" ").optionalEnd();
                    b.appendOffsetId().appendLiteral("[").appendZoneRegionId().appendLiteral("]");
                    return b.toFormatter();
                }));
            }
        }
        return FORMATS;
    }

    @Override
    @Value Temporal fromTemporal(TemporalAccessor temporalAccessor)
    {
        return ZonedDateTime.from(temporalAccessor);
    }

    private class D_TZ extends FunctionInstance
    {
        @Override
        public @Value Object getValue(int rowIndex, @Value Object simpleParams) throws UserException, InternalException
        {
            @Value Object[] paramList = Utility.valueTuple(simpleParams, 2);
            OffsetTime t = (OffsetTime)paramList[1];
            return ZonedDateTime.of((LocalDate)paramList[0], t.toLocalTime(), t.getOffset());
        }
    }

    private class DT_Z extends FunctionInstance
    {
        @Override
        public @Value Object getValue(int rowIndex, @Value Object simpleParams) throws UserException, InternalException
        {
            @Value Object[] paramList = Utility.valueTuple(simpleParams, 2);
            return ZonedDateTime.of((LocalDateTime)paramList[0], ZoneId.of((String)paramList[1]));
        }
    }

    private class D_T_Z extends FunctionInstance
    {
        @Override
        public @Value Object getValue(int rowIndex, @Value Object simpleParams) throws UserException, InternalException
        {
            @Value Object[] paramList = Utility.valueTuple(simpleParams, 3);
            return ZonedDateTime.of(LocalDateTime.of((LocalDate) paramList[0], (LocalTime)paramList[1]), ZoneId.of((String) paramList[2]));
        }
    }
}
