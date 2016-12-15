package records.transformations.function;

import org.checkerframework.checker.nullness.qual.NonNull;
import records.data.columntype.CleanDateColumnType;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.error.InternalException;
import records.error.UserException;
import utility.ExConsumer;

import java.time.LocalDate;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Created by neil on 14/12/2016.
 */
public class StringToDate extends StringToTemporalFunction
{
    public StringToDate()
    {
        super("date");
    }

    public static List<List<String>> formats = Arrays.asList(
        l("dd/MMM/yyyy"),
        l("dd-MMM-yyyy"),
        l("dd MMM yyyy"),
        l("d/MMM/yyyy"),
        l("d-MMM-yyyy"),
        l("d MMM yyyy"),
        l("MMM d yyyy"),
        l("MMM dd yyyy"),
        l("yyyy-MMM-dd"),
        l("yyyy-MM-dd"),
        l("yyyy-MM-d"),
        l("yyyy-M-dd"),
        l("yyyy-M-d"),
        l("d/M/yyyy", "M/d/yyyy"),
        l("dd/M/yyyy", "MM/d/yyyy"),
        l("d/MM/yyyy", "M/dd/yyyy"),
        l("dd/MM/yyyy", "MM/dd/yyyy"),
        l("d/M/yy", "M/d/yy"),
        l("dd/M/yy", "MM/d/yy"),
        l("d/MM/yy", "M/dd/yy"),
        l("dd/MM/yy", "MM/dd/yy"),
        
        l("d.M.yyyy", "M.d.yyyy"),
        l("dd.M.yyyy", "MM.d.yyyy"),
        l("d.MM.yyyy", "M.dd.yyyy"),
        l("dd.MM.yyyy", "MM.dd.yyyy"),
        l("d.M.yy", "M.d.yy"),
        l("dd.M.yy", "MM.d.yy"),
        l("d.MM.yy", "M.dd.yy"),
        l("dd.MM.yy", "MM.dd.yy")
    );
    // TODO make a test that ambiguous ones are, and others aren't

    private static List<String> l(String... args)
    {
        return Arrays.asList(args);
    }

    @Override
    boolean checkTemporalParam(DateTimeInfo srcTemporalType, ExConsumer<String> onError) throws InternalException, UserException
    {
        if (!srcTemporalType.hasYearMonthDay())
        {
            onError.accept("Parameter is a time type, without a date aspect to extract");
            return false;
        }
        return true;
    }

    @Override
    DateTimeInfo getResultType()
    {
        return new DataType.DateTimeInfo(DateTimeType.YEARMONTHDAY);
    }

    @Override
    protected List<List<@NonNull String>> getFormats()
    {
        return formats;
    }

    @Override
    Temporal fromTemporal(TemporalAccessor temporalAccessor)
    {
        return LocalDate.from(temporalAccessor);
    }

}
