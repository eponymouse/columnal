package records.transformations.function;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.datatype.NumberInfo;
import records.data.datatype.DataTypeUtility;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import utility.Utility;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static records.transformations.function.ToTemporalFunction.F.AMPM;
import static records.transformations.function.ToTemporalFunction.F.FRAC_SEC_OPT;
import static records.transformations.function.ToTemporalFunction.F.HOUR;
import static records.transformations.function.ToTemporalFunction.F.HOUR12;
import static records.transformations.function.ToTemporalFunction.F.MIN;
import static records.transformations.function.ToTemporalFunction.F.SEC_OPT;


/**
 * Created by neil on 15/12/2016.
 */
public class ToTime extends ToTemporalFunction
{
    public ToTime()
    {
        super("time", "time.short");
    }

    public static List<DateTimeFormatter> FORMATS = Arrays.asList(
        m(":", HOUR, MIN, SEC_OPT, FRAC_SEC_OPT), // HH:mm[:ss[.S]]
        m(":", HOUR12, MIN, SEC_OPT, FRAC_SEC_OPT, AMPM) // hh:mm[:ss[.S]] PM
    );

    @Override
    public ImmutableList<FunctionDefinition> getTemporalFunctions(UnitManager mgr) throws InternalException
    {
        ImmutableList.Builder<FunctionDefinition> r = ImmutableList.builder();
        r.add(fromString("time.from.text", "time.from.string.mini"));
        r.add(new FunctionDefinition("time.from.datetime", "time.from.datetime.mini", FromTemporalInstance::new, DataType.date(getResultType()), DataType.date(new DateTimeInfo(DateTimeType.DATETIME))));
        r.add(new FunctionDefinition("time.from.datetimezoned", "time.from.datetimezoned.mini", FromTemporalInstance::new, DataType.date(getResultType()), DataType.date(new DateTimeInfo(DateTimeType.DATETIMEZONED))));
        r.add(new FunctionDefinition("time.from.timezoned", "time.from.timezoned.mini", FromTemporalInstance::new, DataType.date(getResultType()), DataType.date(new DateTimeInfo(DateTimeType.TIMEOFDAYZONED))));
        r.add(new FunctionDefinition("time", "time.mini", FromNumbers::new, DataType.date(getResultType()), DataType.tuple(
            DataType.number(new NumberInfo(mgr.loadBuiltIn("hour"))),
            DataType.number(new NumberInfo(mgr.loadBuiltIn("min"))),
            DataType.number(new NumberInfo(mgr.loadBuiltIn("s")))
        )));
        return r.build();
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

    @NonNull
    private List<String> l(String o)
    {
        return Collections.<String>singletonList(o);
    }

    @Override
    @Value Temporal fromTemporal(TemporalAccessor temporalAccessor)
    {
        return LocalTime.from(temporalAccessor);
    }

    private class FromNumbers extends FunctionInstance
    {
        @Override
        public @Value Object getValue(int rowIndex, @Value Object simpleParams) throws UserException, InternalException
        {
            @Value Object[] paramList = Utility.valueTuple(simpleParams, 3);
            int hour = DataTypeUtility.requireInteger(paramList[0]);
            int minute = DataTypeUtility.requireInteger(paramList[1]);
            Number second = (Number)paramList[2];
            return LocalTime.of(hour, minute, DataTypeUtility.requireInteger(DataTypeUtility.value(Utility.getIntegerPart(second))), Utility.getFracPart(second, 9).intValue());
        }
    }
}
