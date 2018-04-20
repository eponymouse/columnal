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
import utility.Utility;
import utility.ValueFunction;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.util.Arrays;
import java.util.List;

import static records.transformations.function.ToTemporalFunction.F.DAY;
import static records.transformations.function.ToTemporalFunction.F.MONTH_NUM;
import static records.transformations.function.ToTemporalFunction.F.MONTH_TEXT_SHORT;
import static records.transformations.function.ToTemporalFunction.F.MONTH_TEXT_LONG;
import static records.transformations.function.ToTemporalFunction.F.YEAR2;
import static records.transformations.function.ToTemporalFunction.F.YEAR4;

/**
 * Created by neil on 14/12/2016.
 */
public class ToDate extends ToTemporalFunction
{
    // All the formats here use space as a separator, and assume that
    // the items have been fed through the pre-process function in here.
    public static List<List<DateTimeFormatter>> FORMATS = Arrays.asList(
        l(m(" ", DAY, MONTH_TEXT_SHORT, YEAR4)), // dd MMM yyyy
        l(m(" ", DAY, MONTH_TEXT_LONG, YEAR4)), // dd MMM yyyy

        l(m(" ", MONTH_TEXT_SHORT, DAY, YEAR4)), // MMM dd yyyy
        l(m(" ", MONTH_TEXT_LONG, DAY, YEAR4)), // MMM dd yyyy

        l(m(" ", YEAR4, MONTH_TEXT_SHORT, DAY)), // yyyy MMM dd

        l(m(" ", YEAR4, MONTH_NUM, DAY)), // yyyy MM dd

        l(m(" ", DAY, MONTH_NUM, YEAR4), m(" ", MONTH_NUM, DAY, YEAR4)), // dd MM yyyy or MM dd yyyy

        l(m(" ", DAY, MONTH_NUM, YEAR2), m(" ", MONTH_NUM, DAY, YEAR2)) // dd MM yy or MM dd yy
    );

    @Override
    public ImmutableList<FunctionDefinition> getTemporalFunctions(UnitManager mgr) throws InternalException
    {
        ImmutableList.Builder<FunctionDefinition> r = ImmutableList.builder();
        r.add(fromString("date.from.string", "date.from.string.mini"));
        r.add(new FunctionDefinition("date.from.datetime", "date.from.datetime.mini", FromTemporalInstance::new, DataType.date(getResultType()), DataType.date(new DateTimeInfo(DateTimeType.DATETIME))));
        r.add(new FunctionDefinition("date.from.datetimezoned", "date.from.datetimezoned.mini", FromTemporalInstance::new, DataType.date(getResultType()), DataType.date(new DateTimeInfo(DateTimeType.DATETIMEZONED))));
        r.add(new FunctionDefinition("date.from.ym.day", "date.from.ym.day.mini", FromYearMonth_Day::new, DataType.date(getResultType()), DataType.tuple(DataType.date(new DateTimeInfo(DateTimeType.YEARMONTH)), DataType.number(new NumberInfo(mgr.loadBuiltIn("day"))))));
        r.add(new FunctionDefinition("date", "date.mini", FromNumbers::new, DataType.date(getResultType()), DataType.tuple(
            DataType.number(new NumberInfo(mgr.loadBuiltIn("year"))),
            DataType.number(new NumberInfo(mgr.loadBuiltIn("month"))),
            DataType.number(new NumberInfo(mgr.loadBuiltIn("day")))
        )));
        return r.build();
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
    @Value Temporal fromTemporal(TemporalAccessor temporalAccessor)
    {
        return LocalDate.from(temporalAccessor);
    }

    private class FromYearMonth_Day extends ValueFunction
    {
        @Override
        public @Value Object call(@Value Object simpleParams) throws UserException, InternalException
        {
            @Value Object[] paramList = Utility.valueTuple(simpleParams, 2);
            YearMonth ym = (YearMonth) paramList[0];
            int day = DataTypeUtility.requireInteger(paramList[1]);
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

    private class FromNumbers extends ValueFunction
    {
        @Override
        public @Value Object call(@Value Object simpleParams) throws UserException, InternalException
        {
            @Value Object[] paramList = Utility.valueTuple(simpleParams, 3);
            int year = DataTypeUtility.requireInteger(paramList[0]);
            int month = DataTypeUtility.requireInteger(paramList[1]);
            int day = DataTypeUtility.requireInteger(paramList[2]);
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
