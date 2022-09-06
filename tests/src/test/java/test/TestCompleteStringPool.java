package test;

import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.junit.runner.RunWith;
import xyz.columnal.error.InternalException;
import xyz.columnal.utility.DumbObjectPool;

import static org.junit.Assert.assertEquals;

/**
 * Created by neil on 26/10/2016.
 */
@RunWith(JUnitQuickcheck.class)
public class TestCompleteStringPool
{
    private DumbObjectPool<String> pool = new DumbObjectPool<>(String.class, 100, null);

    @Property
    public void sameContent(String s) throws InternalException
    {
        assertEquals(s, pool.pool(s));
    }
}
