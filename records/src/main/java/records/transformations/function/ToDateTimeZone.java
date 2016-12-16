package records.transformations.function;

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
        super("datetimez");
    }

    private static List<List<DateTimeFormatter>> FORMATS = new ArrayList<>();

    @Override
    protected List<FunctionType> getOverloads(UnitManager mgr) throws InternalException, UserException
    {
        ArrayList<FunctionType> r = new ArrayList<>(fromString());
        r.add(new FunctionType(DT_Z::new, DataType.date(getResultType()),
            DataType.date(new DateTimeInfo(DateTimeType.DATETIME)), DataType.TEXT));
        r.add(new FunctionType(D_T_Z::new, DataType.date(getResultType()),
            DataType.date(new DateTimeInfo(DateTimeType.YEARMONTHDAY)),
            DataType.date(new DateTimeInfo(DateTimeType.TIMEOFDAY)), DataType.TEXT));
        r.add(new FunctionType(D_TZ::new, DataType.date(getResultType()),
            DataType.date(new DateTimeInfo(DateTimeType.YEARMONTHDAY)),
            DataType.date(new DateTimeInfo(DateTimeType.TIMEOFDAYZONED))));
        return r;
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
    Temporal fromTemporal(TemporalAccessor temporalAccessor)
    {
        return ZonedDateTime.from(temporalAccessor);
    }

    private class D_TZ extends TaglessFunctionInstance
    {
        @Override
        public Object getSimpleValue(int rowIndex, List<Object> simpleParams) throws UserException, InternalException
        {
            OffsetTime t = (OffsetTime)simpleParams.get(1);
            return ZonedDateTime.of((LocalDate)simpleParams.get(0), t.toLocalTime(), t.getOffset());
        }
    }

    private class DT_Z extends TaglessFunctionInstance
    {
        @Override
        public Object getSimpleValue(int rowIndex, List<Object> simpleParams) throws UserException, InternalException
        {
            return ZonedDateTime.of((LocalDateTime)simpleParams.get(0), ZoneId.of((String)simpleParams.get(1)));
        }
    }

    private class D_T_Z extends TaglessFunctionInstance
    {
        @Override
        public Object getSimpleValue(int rowIndex, List<Object> simpleParams) throws UserException, InternalException
        {
            return ZonedDateTime.of(LocalDateTime.of((LocalDate) simpleParams.get(0), (LocalTime)simpleParams.get(1)), ZoneId.of((String) simpleParams.get(2)));
        }
    }
}
