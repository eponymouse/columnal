package records.transformations.function;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.jetbrains.annotations.NotNull;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.datatype.DataType.NumberInfo;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import utility.ExConsumer;
import utility.Utility;

import java.math.BigDecimal;
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
        super("time");
    }

    public static List<DateTimeFormatter> FORMATS = Arrays.asList(
        m(":", HOUR, MIN, SEC_OPT, FRAC_SEC_OPT), // HH:mm[:ss[.S]]
        m(":", HOUR12, MIN, SEC_OPT, FRAC_SEC_OPT, AMPM) // hh:mm[:ss[.S]] PM
    );

    @Override
    public List<FunctionType> getOverloads(UnitManager mgr) throws InternalException, UserException
    {
        ArrayList<FunctionType> r = new ArrayList<>(fromString());
        r.add(new FunctionType(FromTemporalInstance::new, DataType.date(getResultType()), DataType.date(new DateTimeInfo(DateTimeType.DATETIME))));
        r.add(new FunctionType(FromTemporalInstance::new, DataType.date(getResultType()), DataType.date(new DateTimeInfo(DateTimeType.DATETIMEZONED))));
        r.add(new FunctionType(FromTemporalInstance::new, DataType.date(getResultType()), DataType.date(new DateTimeInfo(DateTimeType.TIMEOFDAYZONED))));
        r.add(new FunctionType(FromNumbers::new, DataType.date(getResultType()), DataType.tuple(
            DataType.number(new NumberInfo(mgr.loadUse("hour"), 0)),
            DataType.number(new NumberInfo(mgr.loadUse("min"), 0)),
            DataType.number(new NumberInfo(mgr.loadUse("s"), 0))
        )));
        return r;
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
            int hour = Utility.requireInteger(paramList[0]);
            int minute = Utility.requireInteger(paramList[1]);
            Number second = (Number)paramList[2];
            return LocalTime.of(hour, minute, Utility.requireInteger(Utility.value(Utility.getIntegerPart(second))), Utility.getFracPart(second, 9).intValue());
        }
    }
}
