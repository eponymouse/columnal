import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.junit.runner.RunWith;
import utility.CompleteStringPool;

import static org.junit.Assert.assertEquals;

/**
 * Created by neil on 26/10/2016.
 */
@RunWith(JUnitQuickcheck.class)
public class TestCompleteStringPool
{
    private CompleteStringPool pool = new CompleteStringPool(100);

    @Property
    public void sameContent(String s)
    {
        assertEquals(s, pool.pool(s));
    }
}
