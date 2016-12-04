package test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

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
        less(o(), o("hi"));
        equal(o("hi"), o("hi"));
        equal(o("hi", "bye"), o("hi", "bye"));

        less(o("hi", "bye"), o("hi5", "bye"));
        less(o("hi", "bye"), o("hi", "bye2"));
        less(o("hi", "bye"), o("hi5"));
        
        equal(o(i(2), "hello"), o(i(2), "hello"));
        less(o(i(1), "hello"), o(i(2), "hello"));
        less(o(i(2), "hello"), o(i(2), "helloX"));
        
        equal(o(i(2)), o(by(2)));
        less(o(i(1)), o(by(2)));
        equal(o(l(2)), o(i(2)));
        less(o(l(1)), o(by(2)));
        
        equal(o(d(1.0)), o(i(1)));
        less(o(d(0.99)), o(i(1)));
        less(o(i(1)), o(d(1.01)));
    }

    private BigDecimal d(double v)
    {
        return new BigDecimal(v);
    }

    private Long l(int i)
    {
        return (long)i;
    }

    private Byte by(int i)
    {
        return (byte)i;
    }

    private Integer i(int i)
    {
        return i;
    }

    private static List<Object> o(Object... os)
    {
        return Arrays.asList(os);
    }
    
    private static void equal(List<Object> a, List<Object> b) throws InternalException
    {
        assertEquals(0, Utility.compareLists(a, b));
        assertEquals(0, Utility.compareLists(b, a));
        assertEquals(0, Utility.compareLists(o(b), o(a)));
    }

    private static void less(List<Object> a, List<Object> b) throws InternalException
    {
        assertEquals(-1, Utility.compareLists(a, b));
        assertEquals(1, Utility.compareLists(b, a));
        assertEquals(-1, Utility.compareLists(o(a), o(b)));
        assertEquals(1, Utility.compareLists(o(b), o(a)));
    }
}
