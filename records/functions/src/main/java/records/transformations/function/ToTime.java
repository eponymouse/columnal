package records.transformations.function;

import annotation.funcdoc.qual.FuncDocKey;
import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.NonNull;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.SimulationFunction;
import utility.Utility;
import records.transformations.expression.function.ValueFunction;

import java.time.LocalTime;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.util.Collections;
import java.util.List;

/**
 * Created by neil on 15/12/2016.
 */
public class ToTime extends ToTemporalFunction
{

    public static final @FuncDocKey String TIME_FROM_DATETIME = "datetime:time from datetime";

    @Override
    public ImmutableList<FunctionDefinition> getTemporalFunctions(UnitManager mgr) throws InternalException
    {
        ImmutableList.Builder<FunctionDefinition> r = ImmutableList.builder();
        r.add(new FromTemporal(TIME_FROM_DATETIME));
        r.add(new FromTemporal("datetime:time from datetimezoned"));
        r.add(new FunctionDefinition("datetime:time from hms") {
            @Override
            public @OnThread(Tag.Simulation) ValueFunction getInstance(TypeManager typeManager, SimulationFunction<String, Either<Unit, DataType>> paramTypes) throws InternalException, UserException
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
        public @Value Object _call() throws UserException, InternalException
        {
            int hour = DataTypeUtility.requireInteger(arg(0));
            int minute = DataTypeUtility.requireInteger(arg(1));
            @Value Number second = arg(2, Number.class);
            return LocalTime.of(hour, minute, DataTypeUtility.requireInteger(Utility.getIntegerPart(second)), Utility.getFracPart(second, 9).intValue());
        }
    }
}
