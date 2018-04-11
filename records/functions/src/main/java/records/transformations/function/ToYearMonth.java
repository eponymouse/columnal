package records.transformations.function;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.datatype.NumberInfo;
import records.data.datatype.DataTypeUtility;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import utility.Utility;
import utility.ValueFunction;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.util.Arrays;
import java.util.List;

/**
 * Created by neil on 16/12/2016.
 */
public class ToYearMonth extends ToTemporalFunction
{
    public ToYearMonth()
    {
        super("dateym", "dateym.short");
    }
    
    ImmutableList<FunctionDefinition> getTemporalFunctions(UnitManager mgr) throws InternalException
    {
        ImmutableList.Builder<FunctionDefinition> r = ImmutableList.builder();
        r.add(fromString("dateym.from.string", "dateym.from.string.mini"));
        r.add(new FunctionDefinition("dateym.from.date", "dateym.from.date.mini", FromTemporalInstance::new, DataType.date(getResultType()), DataType.date(new DateTimeInfo(DateTimeType.YEARMONTHDAY))));
        r.add(new FunctionDefinition("dateym.from.datetime", "dateym.from.datetime.mini", FromTemporalInstance::new, DataType.date(getResultType()), DataType.date(new DateTimeInfo(DateTimeType.DATETIME))));
        r.add(new FunctionDefinition("dateym.from.datetimezoned", "dateym.from.datetimezoned.mini", FromTemporalInstance::new, DataType.date(getResultType()), DataType.date(new DateTimeInfo(DateTimeType.DATETIMEZONED))));
        r.add(new FunctionDefinition("dateym", "dateym.mini", FromNumbers::new, DataType.date(getResultType()), DataType.tuple(
            DataType.number(new NumberInfo(mgr.loadBuiltIn("year"))),
            DataType.number(new NumberInfo(mgr.loadBuiltIn("month")))
        )));
        return r.build();
    }

    @Override
    DateTimeInfo getResultType()
    {
        return new DateTimeInfo(DateTimeType.YEARMONTH);
    }

    // We assume source text has been through ToDate.preprocessDate
    @Override
    protected List<List<DateTimeFormatter>> getFormats()
    {
        return Arrays.asList(
            l(m(" ", F.MONTH_NUM, F.YEAR4)),
            l(m(" ", F.MONTH_TEXT_SHORT, F.YEAR2)),
            l(m(" ", F.MONTH_TEXT_SHORT, F.YEAR4)),
            l(m(" ", F.MONTH_TEXT_LONG, F.YEAR4)),
            l(m(" ", F.YEAR4, F.MONTH_NUM))
        );
    }

    @Override
    @Value Temporal fromTemporal(TemporalAccessor temporalAccessor)
    {
        return YearMonth.from(temporalAccessor);
    }

    private class FromNumbers extends ValueFunction
    {
        @Override
        public @Value Object call(@Value Object simpleParams) throws UserException, InternalException
        {
            @Value Object[] paramList = Utility.valueTuple(simpleParams, 2);
            return YearMonth.of(DataTypeUtility.requireInteger(paramList[0]), DataTypeUtility.requireInteger(paramList[1]));
        }
    }
}
