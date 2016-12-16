package test;

import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.function.FunctionDefinition;
import records.transformations.function.FunctionInstance;
import records.transformations.function.ToDate;
import records.transformations.function.ToDateTime;
import records.transformations.function.ToDateTimeZone;
import records.transformations.function.ToTime;
import test.gen.GenDate;
import test.gen.GenZoneId;
import utility.Pair;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

/**
 * Created by neil on 14/12/2016.
 */
@RunWith(JUnitQuickcheck.class)
public class PropDateFunctions
{
    private @MonotonicNonNull UnitManager mgr;
    private List<Pair<LocalDate, String>> dates = new ArrayList<>();
    private List<Pair<LocalTime, String>> times = new ArrayList<>();

    @Before
    public void init() throws UserException, InternalException
    {
        mgr = new UnitManager();
    }

    @Property
    public void testStringToDate(@From(GenDate.class) LocalDate src) throws Throwable
    {
        // Test with string input:
        Object o = strToDate(src.toString());
        assertEquals(LocalDate.class, o.getClass());
        assertEquals(src, o);
    }

    @Property
    public void testStringToTime(LocalTime src) throws Throwable
    {
        // Test with string input:
        Object o = strToTime(src.toString());
        assertEquals(LocalTime.class, o.getClass());
        assertEquals(src, o);
    }

    @Property
    public void testStringToDateTime(@From(GenDate.class) LocalDate date, LocalTime time) throws Throwable
    {
        LocalDateTime src = LocalDateTime.of(date, time);
        // Test with string input:
        Object o = runFunction1(src.toString(), DataType.TEXT, new ToDateTime());
        assertEquals(LocalDateTime.class, o.getClass());
        assertEquals(src, o);
    }

    @Property
    public void testStringToDateTimeZone(@From(GenDate.class) LocalDate date, LocalTime time, @From(GenZoneId.class) ZoneId zone) throws Throwable
    {
        ZonedDateTime src = ZonedDateTime.of(date, time, zone);
        // Test with string input:
        Object o = runFunction1(src.toLocalDateTime().toString() + " " + zone.toString(), DataType.TEXT, new ToDateTimeZone());
        assertEquals(ZonedDateTime.class, o.getClass());
        assertEquals(src.withFixedOffsetZone(), o);
    }

    @Test
    public void testFromString() throws Throwable
    {
        checkDate(LocalDate.of(2016, 12, 01), "2016-12-01");
        checkDate(LocalDate.of(2001, 01, 01), "2001-01-01");
        checkDate(LocalDate.of(2001, 01, 01), "1 Jan 2001");
        checkDate(LocalDate.of(2001, 01, 01), "1-Jan-2001");
        checkDate(LocalDate.of(2001, 01, 01), "Jan 1 2001");
        assertThrows(UserException.class, () -> strToDate("01/01/01"));
        checkDate(LocalDate.of(2013, 12, 13), "13/12/13");
        checkDate(LocalDate.of(2012, 12, 13), "13/12/12");
        assertThrows(UserException.class, () -> strToDate("12/12/12"));
        checkDate(LocalDate.of(9345, 8, 6), "9345-08-06");

        assertThrows(UserException.class, () -> strToTime("1:2"));
        checkTime(LocalTime.of(1, 2), "1:02");
        checkTime(LocalTime.of(21, 2), "21:02");
        checkTime(LocalTime.of(21, 2, 34), "21:02:34");
        assertThrows(UserException.class, () -> strToTime("21:02:3"));
        checkTime(LocalTime.of(21, 2, 34, 0), "21:02:34.0");
        checkTime(LocalTime.of(21, 2, 34, 20_000_000), "21:02:34.020");
        checkTime(LocalTime.of(21, 2, 34, 3_000_000), "21:02:34.003");
        checkTime(LocalTime.of(21, 2, 34, 100_000), "21:02:34.0001");
        checkTime(LocalTime.of(21, 2, 34, 7), "21:02:34.000000007");
        assertThrows(UserException.class, () -> strToTime("21:02:34.0000000007"));

        checkTime(LocalTime.of(1, 2), "1:02AM");
        checkTime(LocalTime.of(11, 59), "11:59AM");
        checkTime(LocalTime.of(0, 2), "12:02AM");
        checkTime(LocalTime.of(12, 2), "12:02PM");
        checkTime(LocalTime.of(0, 2), "12:02 AM");
        checkTime(LocalTime.of(12, 2), "12:02 PM");
        checkTime(LocalTime.of(3, 2, 34), "3:02:34 AM");
        checkTime(LocalTime.of(15, 2, 34), "3:02:34 PM");
        assertThrows(UserException.class, () -> strToTime("20:06 PM"));

        // Must come after checkDate and checkTime calls
        checkDateTimes();
    }

    private void checkDateTimes() throws Throwable
    {
        for (Pair<LocalTime, String> time : times)
        {
            for (Pair<LocalDate, String> date : dates)
            {
                checkDateTime(LocalDateTime.of(date.getFirst(), time.getFirst()), date.getSecond() + " " + time.getSecond());
                for (String zone : Arrays.asList("UTC", " UTC", "+00:00", "+05:30", "-03:00", "UTC-07:00", "America/New_York", " Europe/London"))
                {
                    checkDateTimeZone(ZonedDateTime.of(date.getFirst(), time.getFirst(), ZoneId.of(zone.trim())).withFixedOffsetZone(), date.getSecond() + " " + time.getSecond() + zone);
                }
            }
        }
    }

    private void checkTime(LocalTime of, String src) throws Throwable
    {
        times.add(new Pair<>(of, src));
        assertEquals(of, strToTime(src));
    }

    private void checkDate(LocalDate of, String src) throws Throwable
    {
        dates.add(new Pair<>(of, src));
        assertEquals(of, strToDate(src));
    }

    private void checkDateTime(LocalDateTime of, String src) throws Throwable
    {
        assertEquals(of, runFunction1(src, DataType.TEXT, new ToDateTime()));
    }

    private void checkDateTimeZone(ZonedDateTime of, String src) throws Throwable
    {
        assertEquals(of, runFunction1(src, DataType.TEXT, new ToDateTimeZone()));
    }

    private Object strToTime(String src) throws Throwable
    {
        return runFunction1(src, DataType.TEXT, new ToTime());
    }

    private Object strToDate(String src) throws Throwable
    {
        return runFunction1(src, DataType.TEXT, new ToDate());
    }

    // Tests single numeric input, numeric output function
    @SuppressWarnings("nullness")
    private Object runFunction1(Object src, DataType srcType, FunctionDefinition function) throws InternalException, UserException, Throwable
    {
        try
        {
            @Nullable Pair<FunctionInstance, DataType> instance = function.typeCheck(Collections.emptyList(), Collections.singletonList(srcType), s ->
            {
                throw new RuntimeException(new UserException(s));
            }, mgr);
            assertNotNull(instance);
            return instance.getFirst().getValue(0, Collections.singletonList(Collections.singletonList(src))).get(0);
        }
        catch (RuntimeException e)
        {
            if (e.getCause() != null)
                throw e.getCause();
            else
                throw e;
        }
    }
}
