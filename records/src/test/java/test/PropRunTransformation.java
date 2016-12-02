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
import test.gen.GenRandom;
import test.gen.GenTable;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

import java.util.Collections;
import java.util.Random;

import static org.junit.Assert.assertTrue;

/**
 * Created by neil on 01/12/2016.
 */
@RunWith(JUnitQuickcheck.class)
public class PropRunTransformation
{
    @Property
    @OnThread(Tag.Simulation)
    public void testSort(@From(GenTable.class) Table srcTable, @From(GenRandom.class) Random r) throws UserException, InternalException
    {
        // Pick an arbitrary column and sort by it
        RecordSet src = srcTable.getData();
        Column sortBy = src.getColumns().get(r.nextInt(src.getColumns().size()));

        Sort sort = new Sort(DummyManager.INSTANCE, null, srcTable.getId(), Collections.singletonList(sortBy.getName()));

        // TODO sort by multiple columns, too
        // TODO test compareLists, separately

        assertTrue("Sorting by " + sortBy.getName() + ":\n" + src.debugGetVals(), TestUtil.streamFlattened(sort.getData().getColumn(sortBy.getName()))
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
            .has(true));
        //TODO also check that all the rows which occurred originally, occur again with same frequencies
    }
}
