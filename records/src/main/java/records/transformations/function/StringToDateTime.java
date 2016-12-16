package records.transformations.function;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.error.InternalException;
import records.error.UserException;
import utility.ExConsumer;
import utility.Pair;
import utility.Utility;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
public class StringToDateTime extends StringToTemporalFunction
{
    public StringToDateTime()
    {
        super("datetime");
    }

    @Override
    boolean checkTemporalParam(DateTimeInfo srcTemporalType, ExConsumer<String> onError) throws InternalException, UserException
    {
        return srcTemporalType.hasYearMonthDay() && srcTemporalType.hasTime();
    }

    private static List<List<DateTimeFormatter>> FORMATS = new ArrayList<>();

    @Override
    @Nullable Pair<FunctionInstance, DataType> checkTwoParam(List<DataType> params, ExConsumer<String> onError) throws UserException, InternalException
    {
        if (params.get(0).isDateTime() && params.get(1).isDateTime())
        {
            if (params.get(0).getDateTimeInfo().hasYearMonthDay() && !params.get(0).getDateTimeInfo().hasTime()
                && !params.get(1).getDateTimeInfo().hasYearMonthDay() && params.get(1).getDateTimeInfo().hasTime() && !params.get(1).getDateTimeInfo().hasZone())
            {
                // Works!
                return new Pair<>(new DateAndTimeInstance(), DataType.date(getResultType()));
            }
        }
        return super.checkTwoParam(params, onError);
    }

    @Override
    DateTimeInfo getResultType()
    {
        return new DateTimeInfo(DateTimeType.DATETIME);
    }

    @Override
    protected List<List<DateTimeFormatter>> getFormats()
    {
        if (FORMATS.isEmpty())
        {
            for (@NonNull DateTimeFormatter timeFormat : StringToTime.FORMATS)
            {
                for (List<DateTimeFormatter> dateFormats : StringToDate.FORMATS)
                {
                    List<DateTimeFormatter> newFormats = Utility.<DateTimeFormatter, DateTimeFormatter>mapList(dateFormats, f -> new DateTimeFormatterBuilder().append(f).appendLiteral(" ").append(timeFormat).toFormatter());
                    FORMATS.add(newFormats);
                }
            }
        }
        return FORMATS;
    }

    @Override
    Temporal fromTemporal(TemporalAccessor temporalAccessor)
    {
        return LocalDateTime.from(temporalAccessor);
    }

    private class DateAndTimeInstance extends FunctionInstance
    {
        @Override
        public List<Object> getValue(int rowIndex, List<List<Object>> params) throws UserException, InternalException
        {
            return Collections.singletonList(LocalDateTime.of((LocalDate)params.get(0).get(0), (LocalTime) params.get(1).get(0)));
        }
    }
}
