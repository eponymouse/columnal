package records.transformations.function;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.columntype.CleanDateColumnType;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.Expression;
import records.transformations.expression.Expression._test_TypeVary;
import utility.ExConsumer;
import utility.Pair;
import utility.Utility;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Created by neil on 14/12/2016.
 */
public class StringToDate extends FunctionDefinition
{
    public StringToDate()
    {
        super("date");
    }

    @Override
    public @Nullable Pair<FunctionInstance, DataType> typeCheck(List<Unit> units, List<DataType> params, ExConsumer<String> onError, UnitManager mgr) throws InternalException, UserException
    {
        // TODO allow format as second parameter
        @Nullable DataType t = checkSingleParam(params, onError);
        if (t == null)
            return null;
        if (!t.isText() && !t.isDateTime())
        {
            onError.accept("Parameter must be a string or a date");
            return null;
        }
        if (t.isDateTime() && !t.getDateTimeInfo().hasYearMonthDay())
        {
            onError.accept("Parameter is a time type, without a date aspect to extract");
            return null;
        }
        // If it is a date, check it's got a date component
        return new Pair<>(t.isText() ? new FromStringInstance() : new FromDateInstance(), DataType.date(new DataType.DateTimeInfo(DateTimeType.YEARMONTHDAY)));
    }

    @Override
    public Pair<List<Unit>, List<Expression>> _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType) throws UserException, InternalException
    {
        //TODO test giving units
        return new Pair<>(Collections.emptyList(), Collections.singletonList(newExpressionOfDifferentType.getType(t -> {
            try
            {
                return !t.isText() && (!t.isDateTime() || !t.getDateTimeInfo().hasYearMonthDay());
            }
            catch (InternalException e)
            {
                return false;
            }
        })));
    }

    private static class FromStringInstance extends FunctionInstance
    {
        private ArrayList<Pair<DateTimeFormatter, Integer>> usedFormats = new ArrayList<>();
        private ArrayList<DateTimeFormatter> unusedFormats = new ArrayList<>(CleanDateColumnType.DATE_FORMATS.stream().<DateTimeFormatter>map(DateTimeFormatter::ofPattern).collect(Collectors.<DateTimeFormatter>toList()));

        @Override
        public List<Object> getValue(int rowIndex, List<List<Object>> params) throws UserException
        {
            String src = (String) params.get(0).get(0);

            for (int i = 0; i < usedFormats.size(); i++)
            {
                Pair<DateTimeFormatter, Integer> format = usedFormats.get(i);
                try
                {
                    LocalDate parsed = format.getFirst().parse(src, LocalDate::from);
                    // Didn't throw, so record as used once more:
                    usedFormats.set(i, format.replaceSecond(format.getSecond() + 1));
                    // We only need to sort if we passed the one before us (equal is still fine):
                    if (i > 0 && usedFormats.get(i).getSecond() < usedFormats.get(i - 1).getSecond())
                        Collections.sort(usedFormats, Comparator.comparing(Pair::getSecond));
                    return Collections.singletonList(parsed);
                }
                catch (DateTimeParseException e)
                {
                    // Not this one, then...
                }
            }

            // Try other formats:
            for (Iterator<DateTimeFormatter> iterator = unusedFormats.iterator(); iterator.hasNext(); )
            {
                DateTimeFormatter format = iterator.next();
                try
                {
                    LocalDate parsed = format.parse(src, LocalDate::from);
                    // Didn't throw, so record as used:
                    iterator.remove();
                    // No need to sort; frequency 1 will always be at end of list:
                    usedFormats.add(new Pair<>(format, 1));
                    return Collections.singletonList(parsed);
                }
                catch (DateTimeParseException e)
                {
                    // Not this one, then...
                }
            }

            throw new UserException("Could not parse into date: \"" + src + "\"");
        }
    }

    private static class FromDateInstance extends FunctionInstance
    {
        @Override
        public List<Object> getValue(int rowIndex, List<List<Object>> params) throws UserException
        {
            try
            {
                return Collections.singletonList(LocalDate.from((TemporalAccessor) params.get(0).get(0)));
            }
            catch (DateTimeException e)
            {
                throw new UserException("Could not convert to date: " + e.getLocalizedMessage(), e);
            }
        }
    }
}
