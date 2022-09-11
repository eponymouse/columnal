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

package test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import annotation.qual.Value;
import org.junit.Test;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Utility;
import static org.junit.Assert.assertEquals;

/**
 * Created by neil on 04/12/2016.
 */
public class TestCompare
{
    @Test
    @OnThread(Tag.Simulation)
    public void testCompareList() throws InternalException, UserException
    {
        equal(o(), o());
        less(o(), o(s("hi")));
        equal(o(s("hi")), o(s("hi")));
        equal(o(s("hi"), s("bye")), o(s("hi"), s("bye")));

        less(o(s("hi"), s("bye")), o(s("hi5"), s("bye")));
        less(o(s("hi"), s("bye")), o(s("hi"), s("bye2")));
        less(o(s("hi"), s("bye")), o(s("hi5")));
        
        equal(o(i(2), s("hello")), o(i(2), s("hello")));
        less(o(i(1), s("hello")), o(i(2), s("hello")));
        less(o(i(2), s("hello")), o(i(2), s("helloX")));
        
        equal(o(i(2)), o(by(2)));
        less(o(i(1)), o(by(2)));
        equal(o(l(2)), o(i(2)));
        less(o(l(1)), o(by(2)));
        
        equal(o(d(1.0)), o(i(1)));
        less(o(d(0.99)), o(i(1)));
        less(o(i(1)), o(d(1.01)));
    }

    private @Value BigDecimal d(double v)
    {
        return DataTypeUtility.value(new BigDecimal(v));
    }

    private @Value Long l(int i)
    {
        return DataTypeUtility.value((long)i);
    }

    private @Value Byte by(int i)
    {
        return DataTypeUtility.value((byte)i);
    }

    private @Value Integer i(int i)
    {
        return DataTypeUtility.value(i);
    }

    private @Value String s(String str) { return DataTypeUtility.value(str); }

    private static List<@Value Object> o(@Value Object... os)
    {
        return Arrays.asList(os);
    }

    @OnThread(Tag.Simulation)
    private static void equal(List<@Value Object> a, List<@Value Object> b) throws InternalException, UserException
    {
        assertEquals(0, Utility.compareLists(a, b));
        assertEquals(0, Utility.compareLists(b, a));
    }

    @OnThread(Tag.Simulation)
    private static void less(List<@Value Object> a, List<@Value Object> b) throws InternalException, UserException
    {
        assertEquals(-1, Utility.compareLists(a, b));
        assertEquals(1, Utility.compareLists(b, a));
    }
}
