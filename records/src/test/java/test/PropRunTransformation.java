package test;

import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import one.util.streamex.StreamEx;
import org.junit.runner.RunWith;
import records.data.Column;
import records.data.RecordSet;
import records.data.Table;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.Sort;
import test.gen.GenImmediateData;
import test.gen.GenRandom;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created by neil on 01/12/2016.
 */
@RunWith(JUnitQuickcheck.class)
public class PropRunTransformation
{
    @Property
    @OnThread(Tag.Simulation)
    public void testSort(@From(GenImmediateData.class) GenImmediateData.ImmediateData_Mgr srcTable, @From(GenRandom.class) Random r) throws UserException, InternalException
    {
        // Pick an arbitrary column and sort by it
        RecordSet src = srcTable.data.getData();
        Column sortBy = src.getColumns().get(r.nextInt(src.getColumns().size()));

        Sort sort = new Sort(DummyManager.INSTANCE, null, srcTable.data.getId(), Collections.singletonList(sortBy.getName()));

        // TODO sort by multiple columns, too

        assertTrue("Sorting by " + sortBy.getName() + ":\n" + src.debugGetVals(), !TestUtil.streamFlattened(sort.getData().getColumn(sortBy.getName()))
            .pairMap((a, b) ->
            {
                try
                {
                    int cmp = Utility.compareLists(a, b);
                    if (cmp > 0)
                    {
                        System.err.println("Problematic comparison: " + a + " vs " + b);
                    }
                    return cmp <= 0;
                }
                catch (InternalException e)
                {
                    throw new RuntimeException(e);
                }
            })
            .has(false));
        // Check that the same set of rows is present:
        assertEquals(TestUtil.getRowFreq(src), TestUtil.getRowFreq(sort.getData()));
    }


}
