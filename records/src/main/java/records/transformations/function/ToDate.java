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

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static records.transformations.function.ToTemporalFunction.F.DAY;
import static records.transformations.function.ToTemporalFunction.F.MONTH_NUM;
import static records.transformations.function.ToTemporalFunction.F.MONTH_TEXT;
import static records.transformations.function.ToTemporalFunction.F.YEAR2;
import static records.transformations.function.ToTemporalFunction.F.YEAR4;

/**
 * Created by neil on 14/12/2016.
 */
public class ToDate extends ToTemporalFunction
{
    public ToDate()
    {
        super("date");
    }

    public static List<List<DateTimeFormatter>> FORMATS = Arrays.asList(
        l(m("/", DAY, MONTH_TEXT, YEAR4)), // dd/MMM/yyyy
        l(m("-", DAY, MONTH_TEXT, YEAR4)), // dd-MMM-yyyy
        l(m(" ", DAY, MONTH_TEXT, YEAR4)), // dd MMM yyyy

        l(m(" ", MONTH_TEXT, DAY, YEAR4)), // MMM dd yyyy

        l(m("-", YEAR4, MONTH_TEXT, DAY)), // yyyy-MMM-dd

        l(m("-", YEAR4, MONTH_NUM, DAY)), // yyyy-MM-dd

        l(m("/", DAY, MONTH_NUM, YEAR4), m("/", MONTH_NUM, DAY, YEAR4)), // dd/MM/yyyy or MM/dd/yyyy
        l(m("-", DAY, MONTH_NUM, YEAR4), m("-", MONTH_NUM, DAY, YEAR4)), // dd-MM-yyyy or MM-dd-yyyy
        l(m(".", DAY, MONTH_NUM, YEAR4), m(".", MONTH_NUM, DAY, YEAR4)), // dd.MM.yyyy or MM.dd.yyyy

        l(m("/", DAY, MONTH_NUM, YEAR2), m("/", MONTH_NUM, DAY, YEAR2)), // dd/MM/yyyy or MM/dd/yyyy
        l(m("-", DAY, MONTH_NUM, YEAR2), m("-", MONTH_NUM, DAY, YEAR2)), // dd-MM-yyyy or MM-dd-yyyy
        l(m(".", DAY, MONTH_NUM, YEAR2), m(".", MONTH_NUM, DAY, YEAR2)) // dd.MM.yyyy or MM.dd.yyyy
    );

    @Override
    protected List<FunctionType> getOverloads(UnitManager mgr) throws InternalException, UserException
    {
        ArrayList<FunctionType> r = new ArrayList<>(fromString());
        r.add(new FunctionType(FromTemporalInstance::new, DataType.date(getResultType()), DataType.date(new DateTimeInfo(DateTimeType.DATETIME))));
        r.add(new FunctionType(FromTemporalInstance::new, DataType.date(getResultType()), DataType.date(new DateTimeInfo(DateTimeType.DATETIMEZONED))));
        r.add(new FunctionType(FromYearMonth_Day::new, DataType.date(getResultType()), DataType.date(new DateTimeInfo(DateTimeType.YEARMONTH)), DataType.number(new NumberInfo(mgr.loadUse("day"), 0))));
        r.add(new FunctionType(FromNumbers::new, DataType.date(getResultType()),
            DataType.number(new NumberInfo(mgr.loadUse("year"), 0)),
            DataType.number(new NumberInfo(mgr.loadUse("month"), 0)),
            DataType.number(new NumberInfo(mgr.loadUse("day"), 0))
        ));
        return r;
    }

    @Override
    DateTimeInfo getResultType()
    {
        return new DataType.DateTimeInfo(DateTimeType.YEARMONTHDAY);
    }

    @Override
    protected List<List<@NonNull DateTimeFormatter>> getFormats()
    {
        return FORMATS;
    }

    @Override
    Temporal fromTemporal(TemporalAccessor temporalAccessor)
    {
        return LocalDate.from(temporalAccessor);
    }

    private class FromYearMonth_Day extends TaglessFunctionInstance
    {
        @Override
        public Object getSimpleValue(int rowIndex, List<Object> simpleParams) throws UserException, InternalException
        {
            YearMonth ym = (YearMonth) simpleParams.get(0);
            int day = Utility.requireInteger(simpleParams.get(1));
            try
            {
                return LocalDate.of(ym.getYear(), ym.getMonth(), day);
            }
            catch (DateTimeException e)
            {
                throw new UserException("Invalid date: " + ym.getYear() + ", " + ym.getMonth() + ", " + day + " " + e.getLocalizedMessage(), e);
            }
        }
    }

    private class FromNumbers extends TaglessFunctionInstance
    {
        @Override
        public Object getSimpleValue(int rowIndex, List<Object> simpleParams) throws UserException, InternalException
        {
            int year = Utility.requireInteger(simpleParams.get(0));
            int month = Utility.requireInteger(simpleParams.get(1));
            int day = Utility.requireInteger(simpleParams.get(2));
            try
            {
                return LocalDate.of(year, month, day);
            }
            catch (DateTimeException e)
            {
                throw new UserException("Invalid date: " + year + ", " + month + ", " + day + " " + e.getLocalizedMessage(), e);
            }
        }
    }
}
