package test;

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
        
        equal(o(i(2), "hello"), o(i(2), "hello"));
        
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
    }

    private static void less(List<Object> a, List<Object> b) throws InternalException
    {
        assertEquals(-1, Utility.compareLists(a, b));
        assertEquals(1, Utility.compareLists(b, a));
    }
}
