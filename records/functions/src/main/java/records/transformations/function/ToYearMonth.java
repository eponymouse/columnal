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

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
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
        r.add(fromString("dateym.from.string"));
        r.add(new FunctionDefinition("dateym.from.ymd", FromTemporalInstance::new, DataType.date(getResultType()), DataType.date(new DateTimeInfo(DateTimeType.YEARMONTHDAY))));
        r.add(new FunctionDefinition("dateym.from.datetime", FromTemporalInstance::new, DataType.date(getResultType()), DataType.date(new DateTimeInfo(DateTimeType.DATETIME))));
        r.add(new FunctionDefinition("dateym.from.datetimezoned", FromTemporalInstance::new, DataType.date(getResultType()), DataType.date(new DateTimeInfo(DateTimeType.DATETIMEZONED))));
        r.add(new FunctionDefinition("dateym", FromNumbers::new, DataType.date(getResultType()), DataType.tuple(
            DataType.number(new NumberInfo(mgr.loadBuiltIn("year"), null)),
            DataType.number(new NumberInfo(mgr.loadBuiltIn("month"), null))
        )));
        return r.build();
    }

    @Override
    DateTimeInfo getResultType()
    {
        return new DateTimeInfo(DateTimeType.YEARMONTH);
    }

    @Override
    protected List<List<DateTimeFormatter>> getFormats()
    {
        return Arrays.asList(
            l(m("/", F.MONTH_NUM, F.YEAR4)),
            l(m("-", F.MONTH_NUM, F.YEAR4)),
            l(m("/", F.MONTH_TEXT, F.YEAR4)),
            l(m(" ", F.MONTH_TEXT, F.YEAR4)),
            l(m("-", F.MONTH_TEXT, F.YEAR4)),
            l(m("/", F.YEAR4, F.MONTH_NUM)),
            l(m("-", F.YEAR4, F.MONTH_NUM))
        );
    }

    @Override
    @Value Temporal fromTemporal(TemporalAccessor temporalAccessor)
    {
        return YearMonth.from(temporalAccessor);
    }

    private class FromNumbers extends FunctionInstance
    {
        @Override
        public @Value Object getValue(int rowIndex, @Value Object simpleParams) throws UserException, InternalException
        {
            @Value Object[] paramList = Utility.valueTuple(simpleParams, 2);
            return YearMonth.of(DataTypeUtility.requireInteger(paramList[0]), DataTypeUtility.requireInteger(paramList[1]));
        }
    }
}
