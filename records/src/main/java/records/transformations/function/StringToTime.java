package records.transformations.function;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.jetbrains.annotations.NotNull;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.error.InternalException;
import records.error.UserException;
import utility.ExConsumer;
import utility.Utility;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static records.transformations.function.StringToTemporalFunction.F.AMPM;
import static records.transformations.function.StringToTemporalFunction.F.FRAC_SEC_OPT;
import static records.transformations.function.StringToTemporalFunction.F.HOUR;
import static records.transformations.function.StringToTemporalFunction.F.HOUR12;
import static records.transformations.function.StringToTemporalFunction.F.MIN;
import static records.transformations.function.StringToTemporalFunction.F.SEC_OPT;


/**
 * Created by neil on 15/12/2016.
 */
public class StringToTime extends StringToTemporalFunction
{
    public StringToTime()
    {
        super("time");
    }

    public static List<DateTimeFormatter> FORMATS = Arrays.asList(
        m(":", HOUR, MIN, SEC_OPT, FRAC_SEC_OPT), // HH:mm[:ss[.S]]
        m(":", HOUR12, MIN, SEC_OPT, FRAC_SEC_OPT, AMPM) // hh:mm[:ss[.S]] PM
    );

    @Override
    boolean checkTemporalParam(DateTimeInfo srcTemporalType, ExConsumer<String> onError) throws InternalException, UserException
    {
        return srcTemporalType.hasTime();
    }

    @Override
    DateTimeInfo getResultType()
    {
        return new DateTimeInfo(DateTimeType.TIMEOFDAY);
    }

    @Override
    protected List<List<@NonNull DateTimeFormatter>> getFormats()
    {
        return Utility.<DateTimeFormatter, List<@NonNull DateTimeFormatter>>mapList(FORMATS, f -> Collections.singletonList(f));
    }

    @NotNull
    private List<String> l(String o)
    {
        return Collections.<String>singletonList(o);
    }

    @Override
    Temporal fromTemporal(TemporalAccessor temporalAccessor)
    {
        return LocalTime.from(temporalAccessor);
    }
}
