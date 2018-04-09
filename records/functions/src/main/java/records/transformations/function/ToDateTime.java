package records.transformations.function;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.NonNull;
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
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by neil on 16/12/2016.
 */
public class ToDateTime extends ToTemporalFunction
{
    public ToDateTime()
    {
        super("datetime", "datetime.short");
    }

    private static List<List<DateTimeFormatter>> FORMATS = new ArrayList<>();

    @Override
    public ImmutableList<FunctionDefinition> getTemporalFunctions(UnitManager mgr) throws InternalException
    {
        ImmutableList.Builder<FunctionDefinition> r = ImmutableList.builder();
        r.add(fromString("datetime.from.string", "datetime.from.string.mini"));
        r.add(new FunctionDefinition("datetime.from.datetimezoned", "datetime.from.datetimezoned.mini", FromTemporalInstance::new, DataType.date(getResultType()), DataType.date(new DateTimeInfo(DateTimeType.DATETIMEZONED))));
        r.add(new FunctionDefinition("datetime", "datetime.mini", DateAndTimeInstance::new, DataType.date(getResultType()), DataType.tuple(DataType.date(new DateTimeInfo(DateTimeType.YEARMONTHDAY)), DataType.date(new DateTimeInfo(DateTimeType.TIMEOFDAY)))));
        return r.build();
    }

    @Override
    DateTimeInfo getResultType()
    {
        return new DateTimeInfo(DateTimeType.DATETIME);
    }

    @Override
    protected List<List<DateTimeFormatter>> getFormats()
    {
        return FORMATS();
    }

    static List<List<DateTimeFormatter>> FORMATS()
    {
        if (FORMATS.isEmpty())
        {
            for (@NonNull DateTimeFormatter timeFormat : ToTime.FORMATS)
            {
                for (List<DateTimeFormatter> dateFormats : ToDate.FORMATS)
                {
                    List<DateTimeFormatter> newFormatsSpace = Utility.<DateTimeFormatter, DateTimeFormatter>mapList(dateFormats, f -> new DateTimeFormatterBuilder().append(f).appendLiteral(" ").append(timeFormat).toFormatter());
                    List<DateTimeFormatter> newFormatsT = Utility.<DateTimeFormatter, DateTimeFormatter>mapList(dateFormats, f -> new DateTimeFormatterBuilder().append(f).appendLiteral("T").append(timeFormat).toFormatter());
                    FORMATS.add(newFormatsSpace);
                    FORMATS.add(newFormatsT);
                }
            }
        }
        return FORMATS;
    }

    @Override
    @Value Temporal fromTemporal(TemporalAccessor temporalAccessor)
    {
        return LocalDateTime.from(temporalAccessor);
    }

    private class DateAndTimeInstance extends FunctionInstance
    {
        @Override
        public @Value Object getValue(int rowIndex, @Value Object params) throws UserException, InternalException
        {
            @Value Object[] paramList = Utility.valueTuple(params, 2);
            return LocalDateTime.of((LocalDate)paramList[0], (LocalTime) paramList[1]);
        }
    }
}
