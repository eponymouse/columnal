package records.transformations.function;

import org.checkerframework.checker.nullness.qual.NonNull;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.error.InternalException;
import records.error.UserException;
import utility.ExConsumer;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.util.Arrays;
import java.util.List;

import static records.transformations.function.StringToTemporalFunction.F.DAY;
import static records.transformations.function.StringToTemporalFunction.F.MONTH_NUM;
import static records.transformations.function.StringToTemporalFunction.F.MONTH_TEXT;
import static records.transformations.function.StringToTemporalFunction.F.YEAR2;
import static records.transformations.function.StringToTemporalFunction.F.YEAR4;

/**
 * Created by neil on 14/12/2016.
 */
public class StringToDate extends StringToTemporalFunction
{
    public StringToDate()
    {
        super("date");
    }

    public static List<List<DateTimeFormatter>> FORMATS = Arrays.asList(
        l(m("/", DAY, MONTH_TEXT, YEAR4)), // dd/MMM/yyyy
        l(m("-", DAY, MONTH_TEXT, YEAR4)), // dd-MMM-yyyy
        l(m(" ", DAY, MONTH_TEXT, YEAR4)), // dd MMM yyyy

        l(m(" ", MONTH_TEXT, DAY, YEAR4)), // MMM dd yyyy

        l(m("-", YEAR4, MONTH_TEXT, DAY)), // yyyy-MMM-dd

        l(m("-", YEAR4, MONTH_NUM, DAY)), // yyyy-MM-dd

        l(m("/", DAY, MONTH_NUM, YEAR4), m("/", MONTH_NUM, DAY, YEAR4)), // dd/MM/yyyy or MM/dd/yyyy
        l(m("-", DAY, MONTH_NUM, YEAR4), m("-", MONTH_NUM, DAY, YEAR4)), // dd-MM-yyyy or MM-dd-yyyy
        l(m(".", DAY, MONTH_NUM, YEAR4), m(".", MONTH_NUM, DAY, YEAR4)), // dd.MM.yyyy or MM.dd.yyyy

        l(m("/", DAY, MONTH_NUM, YEAR2), m("/", MONTH_NUM, DAY, YEAR2)), // dd/MM/yyyy or MM/dd/yyyy
        l(m("-", DAY, MONTH_NUM, YEAR2), m("-", MONTH_NUM, DAY, YEAR2)), // dd-MM-yyyy or MM-dd-yyyy
        l(m(".", DAY, MONTH_NUM, YEAR2), m(".", MONTH_NUM, DAY, YEAR2)) // dd.MM.yyyy or MM.dd.yyyy
    );

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
    protected List<List<@NonNull DateTimeFormatter>> getFormats()
    {
        return FORMATS;
    }

    @Override
    Temporal fromTemporal(TemporalAccessor temporalAccessor)
    {
        return LocalDate.from(temporalAccessor);
    }

}
