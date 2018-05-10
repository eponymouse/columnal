package records.transformations.function;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.NonNull;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.datatype.NumberInfo;
import records.data.datatype.DataTypeUtility;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.SimulationFunction;
import utility.Utility;
import utility.ValueFunction;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Created by neil on 15/12/2016.
 */
public class ToTime extends ToTemporalFunction
{

    @Override
    public ImmutableList<FunctionDefinition> getTemporalFunctions(UnitManager mgr) throws InternalException
    {
        ImmutableList.Builder<FunctionDefinition> r = ImmutableList.builder();
        r.add(new FromTemporal("datetime:time from datetime"));
        r.add(new FromTemporal("datetime:time from datetimezoned"));
        r.add(new FunctionDefinition("datetime:time") {
            @Override
            public @OnThread(Tag.Simulation) ValueFunction getInstance(SimulationFunction<String, DataType> paramTypes) throws InternalException, UserException
            {
                return new FromNumbers();
            }});
        return r.build();
    }

    @Override
    DateTimeInfo getResultType()
    {
        return new DateTimeInfo(DateTimeType.TIMEOFDAY);
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

    private class FromNumbers extends ValueFunction
    {
        @Override
        public @Value Object call(@Value Object simpleParams) throws UserException, InternalException
        {
            @Value Object[] paramList = Utility.valueTuple(simpleParams, 3);
            int hour = DataTypeUtility.requireInteger(paramList[0]);
            int minute = DataTypeUtility.requireInteger(paramList[1]);
            Number second = (Number)paramList[2];
            return LocalTime.of(hour, minute, DataTypeUtility.requireInteger(DataTypeUtility.value(Utility.getIntegerPart(second))), Utility.getFracPart(second, 9).intValue());
        }
    }
}
