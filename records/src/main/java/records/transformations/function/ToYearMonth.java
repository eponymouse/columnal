package records.transformations.function;

import org.checkerframework.checker.nullness.qual.NonNull;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.datatype.DataType.NumberInfo;
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
        super("dateym");
    }

    @Override
    protected List<FunctionType> getOverloads(UnitManager mgr) throws InternalException, UserException
    {
        ArrayList<FunctionType> r = new ArrayList<>(fromString());
        r.add(new FunctionType(FromTemporalInstance::new, DataType.date(getResultType()), DataType.date(new DateTimeInfo(DateTimeType.YEARMONTHDAY))));
        r.add(new FunctionType(FromTemporalInstance::new, DataType.date(getResultType()), DataType.date(new DateTimeInfo(DateTimeType.DATETIME))));
        r.add(new FunctionType(FromTemporalInstance::new, DataType.date(getResultType()), DataType.date(new DateTimeInfo(DateTimeType.DATETIMEZONED))));
        r.add(new FunctionType(FromNumbers::new, DataType.date(getResultType()),
            DataType.number(new NumberInfo(mgr.loadUse("year"), 0)),
            DataType.number(new NumberInfo(mgr.loadUse("month"), 0))
        ));
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
    Temporal fromTemporal(TemporalAccessor temporalAccessor)
    {
        return YearMonth.from(temporalAccessor);
    }

    private class FromNumbers extends TaglessFunctionInstance
    {
        @Override
        public Object getSimpleValue(int rowIndex, List<Object> simpleParams) throws UserException, InternalException
        {
            return YearMonth.of(Utility.requireInteger(simpleParams.get(0)), Utility.requireInteger(simpleParams.get(1)));
        }
    }
}
