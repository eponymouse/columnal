package records.transformations.function;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.SimulationFunction;
import records.transformations.expression.function.ValueFunction;

import java.time.YearMonth;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;

/**
 * Created by neil on 16/12/2016.
 */
public class ToYearMonth extends ToTemporalFunction
{

    public static final String DATEYM_FROM_DATE = "datetime:dateym from date";

    ImmutableList<FunctionDefinition> getTemporalFunctions(UnitManager mgr) throws InternalException
    {
        ImmutableList.Builder<FunctionDefinition> r = ImmutableList.builder();
        r.add(new FromTemporal(DATEYM_FROM_DATE));
        /* TODO
        r.add(fromString("dateym.from.string", "dateym.from.string.mini"));
        r.add(new FunctionDefinition("dateym.from.date", "dateym.from.date.mini", FromTemporalInstance::new, DataType.date(getResultType()), DataType.date(new DateTimeInfo(DateTimeType.YEARMONTHDAY))));
        r.add(new FunctionDefinition("dateym.from.datetime", "dateym.from.datetime.mini", FromTemporalInstance::new, DataType.date(getResultType()), DataType.date(new DateTimeInfo(DateTimeType.DATETIME))));
        r.add(new FunctionDefinition("dateym.from.datetimezoned", "dateym.from.datetimezoned.mini", FromTemporalInstance::new, DataType.date(getResultType()), DataType.date(new DateTimeInfo(DateTimeType.DATETIMEZONED))));
        */
        r.add(new FunctionDefinition("datetime:dateym from ym") {
            @Override
            public @OnThread(Tag.Simulation) ValueFunction getInstance(TypeManager typeManager, SimulationFunction<String, Either<Unit, DataType>> paramTypes) throws InternalException, UserException
            {
                return new FromNumbers();
            }
        });
        return r.build();
    }

    @Override
    DateTimeInfo getResultType()
    {
        return new DateTimeInfo(DateTimeType.YEARMONTH);
    }


    @Override
    @Value Temporal fromTemporal(TemporalAccessor temporalAccessor)
    {
        return YearMonth.from(temporalAccessor);
    }

    private class FromNumbers extends ValueFunction
    {
        @Override
        public @Value Object _call() throws UserException, InternalException
        {
            return YearMonth.of(intArg(0), intArg(1));
        }
    }
}
