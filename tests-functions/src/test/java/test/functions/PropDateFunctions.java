/*
 * Columnal: Safer, smoother data table processing.
 * Copyright (c) Neil Brown, 2016-2020, 2022.
 *
 * This file is part of Columnal.
 *
 * Columnal is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Columnal is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for 
 * more details.
 *
 * You should have received a copy of the GNU General Public License along 
 * with Columnal. If not, see <https://www.gnu.org/licenses/>.
 */

package test.functions;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataType.DateTimeInfo;
import xyz.columnal.data.datatype.DataType.DateTimeInfo.DateTimeType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.unit.UnitManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.function.FunctionDefinition;
import xyz.columnal.transformations.function.FunctionList;
import test.DummyManager;
import test.gen.GenDate;
import test.gen.GenZoneId;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.transformations.expression.function.ValueFunction;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
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
    @OnThread(Tag.Simulation)
    public void testStringToDate(@From(GenDate.class) LocalDate src) throws Throwable
    {
        // Test with string input:
        Object o = strTo(src.toString(), DateTimeType.YEARMONTHDAY);
        assertEquals(LocalDate.class, o.getClass());
        assertEquals(src, o);
    }

    @Property
    @OnThread(Tag.Simulation)
    public void testStringToTime(LocalTime src) throws Throwable
    {
        // Test with string input:
        Object o = strTo(src.toString(), DateTimeType.TIMEOFDAY);
        assertEquals(LocalTime.class, o.getClass());
        assertEquals(src, o);
    }

    @Property
    @OnThread(Tag.Simulation)
    public void testStringToDateTime(@From(GenDate.class) LocalDate date, LocalTime time) throws Throwable
    {
        LocalDateTime src = LocalDateTime.of(date, time);
        // Test with string input:
        Object o = strTo(v(src.toString()), DateTimeType.DATETIME);
        assertEquals(LocalDateTime.class, o.getClass());
        assertEquals(src, o);
    }

    // Shorthand for Utility.value
    private @Value String v(String string)
    {
        return DataTypeUtility.value(string);
    }

    @Property
    @OnThread(Tag.Simulation)
    public void testStringToDateTimeZone(@From(GenDate.class) LocalDate date, LocalTime time, @From(GenZoneId.class) ZoneId zone) throws Throwable
    {
        ZonedDateTime src = ZonedDateTime.of(date, time, zone);
        // Test with string input:
        Object o = strTo(v(src.toLocalDateTime().toString() + " " + zone.toString()),  DateTimeType.DATETIMEZONED);
        assertEquals(ZonedDateTime.class, o.getClass());
        assertEquals(src, o);
    }

    @Test
    @OnThread(Tag.Simulation)
    public void testFromString() throws Throwable
    {
        checkDate(LocalDate.of(2016, 12, 01), "2016-12-01");
        checkDate(LocalDate.of(2001, 01, 01), "2001-01-01");
        checkDate(LocalDate.of(2001, 01, 01), "1 Jan 2001");
        checkDate(LocalDate.of(2001, 01, 01), "1-Jan-2001");
        checkDate(LocalDate.of(2001, 01, 01), "Jan 1 2001");
        checkDate(LocalDate.of(2001, 01, 01), "01/01/01");
        assertThrows(UserException.class, () -> strTo("01/02/01", DateTimeType.YEARMONTHDAY));
        checkDate(LocalDate.of(2013, 12, 13), "13/12/13");
        checkDate(LocalDate.of(2012, 12, 13), "13/12/12");
        checkDate(LocalDate.of(2012, 12, 12), "12/12/12");
        assertThrows(UserException.class, () -> strTo("11/12/12", DateTimeType.YEARMONTHDAY));
        checkDate(LocalDate.of(9345, 8, 6), "9345-08-06");
        checkDate(LocalDate.of(2016, 12, 01), "2016 12 01");
        checkDate(LocalDate.of(2016, 12, 01), "2016  12  01");
        checkDate(LocalDate.of(1984, 6, 21), "June 21, 1984");
        checkDate(LocalDate.of(1984, 6, 21), "June 21. 1984\n");
        assertThrows(UserException.class, () -> strTo("June 21. 1984 23:", DateTimeType.YEARMONTHDAY));

        assertThrows(UserException.class, () -> strTo("1:2", DateTimeType.TIMEOFDAY));
        checkTime(LocalTime.of(1, 2), "1:02");
        checkTime(LocalTime.of(21, 2), "21:02");
        checkTime(LocalTime.of(21, 2, 34), "21:02:34");
        assertThrows(UserException.class, () -> strTo("21:02:3", DateTimeType.TIMEOFDAY));
        checkTime(LocalTime.of(21, 2, 34, 0), "21:02:34.0");
        checkTime(LocalTime.of(21, 2, 34, 20_000_000), "21:02:34.020");
        checkTime(LocalTime.of(21, 2, 34, 3_000_000), "21:02:34.003");
        checkTime(LocalTime.of(21, 2, 34, 100_000), "21:02:34.0001");
        checkTime(LocalTime.of(21, 2, 34, 7), "21:02:34.000000007");
        assertThrows(UserException.class, () -> strTo("21:02:34.0000000007", DateTimeType.TIMEOFDAY));

        checkTime(LocalTime.of(1, 2), "1:02AM");
        checkTime(LocalTime.of(11, 59), "11:59AM");
        checkTime(LocalTime.of(0, 2), "12:02AM");
        checkTime(LocalTime.of(12, 2), "12:02PM");
        checkTime(LocalTime.of(0, 2), "12:02 AM");
        checkTime(LocalTime.of(12, 2), "12:02 PM");
        checkTime(LocalTime.of(3, 2, 34), "3:02:34 AM");
        checkTime(LocalTime.of(15, 2, 34), "3:02:34 PM");
        assertThrows(UserException.class, () -> strTo("20:06 PM", DateTimeType.TIMEOFDAY));

        // Must come after checkDate and checkTime calls
        checkDateTimes();
    }

    @OnThread(Tag.Simulation)
    private void checkDateTimes() throws Throwable
    {
        for (Pair<LocalTime, String> time : times)
        {
            for (Pair<LocalDate, String> date : dates)
            {
                checkDateTime(LocalDateTime.of(date.getFirst(), time.getFirst()), date.getSecond() + " " + time.getSecond());
                for (String zone : Arrays.asList("UTC", " UTC", "+00:00", "+05:30", "-03:00", "UTC-07:00", "America/New_York", " Europe/London"))
                {
                    checkDateTimeZone(ZonedDateTime.of(date.getFirst(), time.getFirst(), ZoneId.of(zone.trim())), date.getSecond() + " " + time.getSecond() + zone);
                }
            }
        }
    }

    @OnThread(Tag.Simulation)
    private void checkTime(LocalTime of, String src) throws Throwable
    {
        times.add(new Pair<>(of, src));
        assertEquals(of, strTo(src, DateTimeType.TIMEOFDAY));
    }

    @OnThread(Tag.Simulation)
    private void checkDate(LocalDate of, String src) throws Throwable
    {
        dates.add(new Pair<>(of, src));
        assertEquals(of, strTo(src, DateTimeType.YEARMONTHDAY));
    }

    @OnThread(Tag.Simulation)
    private void checkDateTime(LocalDateTime of, String src) throws Throwable
    {
        assertEquals(of, strTo(src, DateTimeType.DATETIME));
    }

    @OnThread(Tag.Simulation)
    private void checkDateTimeZone(ZonedDateTime of, String src) throws Throwable
    {
        assertEquals(of, strTo(src, DateTimeType.DATETIMEZONED));
    }

    @OnThread(Tag.Simulation)
    private Object strTo(String src, DateTimeType dateTimeType) throws Throwable
    {
        @Nullable FunctionDefinition fromText = FunctionList.lookup(DummyManager.make().getUnitManager(), "from text to");
        if (fromText == null)
            throw new RuntimeException("Cannot find from text to function");
        // First param should be Type Date, but it shouldn't be used....
        ImmutableList<@Value Object> args = ImmutableList.of(v(""), v(src));
        return runFunction1(args, ImmutableList.of(DummyManager.make().getTypeManager().typeGADTFor(DataType.date(new DateTimeInfo(dateTimeType))), DataType.TEXT), fromText);
    }

    // Tests single numeric input, numeric output function
    @SuppressWarnings({"nullness", "value"})
    @OnThread(Tag.Simulation)
    private Object runFunction1(ImmutableList<@Value Object> src, ImmutableList<DataType> srcType, FunctionDefinition function) throws InternalException, UserException, Throwable
    {
        try
        {
            @Nullable Pair<ValueFunction, DataType> instance = TFunctionUtil.typeCheckFunction(function, srcType);
            assertNotNull(instance);
            return instance.getFirst().call(src.toArray(new @Value Object[0]));
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
