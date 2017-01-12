package test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import annotation.qual.Value;
import org.junit.Test;
import records.error.InternalException;
import utility.Utility;
import static org.junit.Assert.assertEquals;

/**
 * Created by neil on 04/12/2016.
 */
public class TestCompare
{
    @Test
    public void testCompareList() throws InternalException
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
        return Utility.value(new BigDecimal(v));
    }

    private @Value Long l(int i)
    {
        return Utility.value((long)i);
    }

    private @Value Byte by(int i)
    {
        return Utility.value((byte)i);
    }

    private @Value Integer i(int i)
    {
        return Utility.value(i);
    }

    private @Value String s(String str) { return Utility.value(str); }

    private static List<@Value Object> o(@Value Object... os)
    {
        return Arrays.asList(os);
    }
    
    private static void equal(List<@Value Object> a, List<@Value Object> b) throws InternalException
    {
        assertEquals(0, Utility.compareLists(a, b));
        assertEquals(0, Utility.compareLists(b, a));
    }

    private static void less(List<@Value Object> a, List<@Value Object> b) throws InternalException
    {
        assertEquals(-1, Utility.compareLists(a, b));
        assertEquals(1, Utility.compareLists(b, a));
    }
}
