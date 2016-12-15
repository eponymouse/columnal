package records.transformations.function;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.jetbrains.annotations.NotNull;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.error.InternalException;
import records.error.UserException;
import utility.ExConsumer;

import java.time.LocalTime;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Created by neil on 15/12/2016.
 */
public class StringToTime extends StringToTemporalFunction
{
    public StringToTime()
    {
        super("time");
    }

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
    protected List<List<@NonNull String>> getFormats()
    {
        return Arrays.<List<String>>asList(
            l("HH:mm"),
            l("H:mm"),
            l("HH:mm:ss"),
            l("H:mm:ss"),
            l("HH:mm:ss.S"),
            l("H:mm:ss.S")
        );
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
