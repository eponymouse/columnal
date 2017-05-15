package records.transformations.function;

import annotation.qual.Value;
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

    @Override
    public List<FunctionType> getOverloads(UnitManager mgr) throws InternalException
    {
        ArrayList<FunctionType> r = new ArrayList<>(fromString("dateym.string"));
        r.add(new FunctionType(FromTemporalInstance::new, DataType.date(getResultType()), DataType.date(new DateTimeInfo(DateTimeType.YEARMONTHDAY)), "dateym.ymd"));
        r.add(new FunctionType(FromTemporalInstance::new, DataType.date(getResultType()), DataType.date(new DateTimeInfo(DateTimeType.DATETIME)), "dateym.datetime"));
        r.add(new FunctionType(FromTemporalInstance::new, DataType.date(getResultType()), DataType.date(new DateTimeInfo(DateTimeType.DATETIMEZONED)), "dateym.datetimez"));
        r.add(new FunctionType(FromNumbers::new, DataType.date(getResultType()), DataType.tuple(
            DataType.number(new NumberInfo(mgr.loadBuiltIn("year"), null)),
            DataType.number(new NumberInfo(mgr.loadBuiltIn("month"), null))
        ), "dateym.year_month"));
        return r;
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
