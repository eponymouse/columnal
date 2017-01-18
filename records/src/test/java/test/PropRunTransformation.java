package test;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
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
import records.transformations.Filter;
import records.transformations.Sort;
import records.transformations.expression.CallExpression;
import records.transformations.expression.ColumnReference;
import records.transformations.expression.ColumnReference.ColumnReferenceType;
import records.transformations.expression.ComparisonExpression;
import records.transformations.expression.ComparisonExpression.ComparisonOperator;
import records.transformations.expression.NumericLiteral;
import test.gen.GenImmediateData;
import test.gen.GenRandom;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeThat;
import static org.junit.Assume.assumeTrue;

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

        Sort sort = new Sort(srcTable.mgr, null, srcTable.data.getId(), Collections.singletonList(sortBy.getName()));

        // TODO sort by multiple columns, too

        assertTrue("Sorting by " + sortBy.getName() + ":\n" + src.debugGetVals(), !TestUtil.streamFlattened(sort.getData().getColumn(sortBy.getName()))
            .pairMap((a, b) ->
            {
                try
                {
                    int cmp = Utility.compareValues(a, b);
                    if (cmp > 0)
                    {
                        System.err.println("Problematic comparison: " + a + " vs " + b);
                    }
                    return cmp <= 0;
                }
                catch (InternalException | UserException e)
                {
                    throw new RuntimeException(e);
                }
            })
            .has(false));
        // Check that the same set of rows is present:
        assertEquals(TestUtil.getRowFreq(src), TestUtil.getRowFreq(sort.getData()));
    }

    @Property
    @OnThread(Tag.Simulation)
    public void testFilter(@From(GenImmediateData.class) GenImmediateData.ImmediateData_Mgr srcTable, @From(GenRandom.class) Random r) throws UserException, InternalException
    {
        // Pick a column to filter on:
        List<Column> columns = srcTable.data.getData().getColumns();
        Column target = columns.get(r.nextInt(columns.size()));

        assumeTrue(target.getLength() > 0);

        // Pick a random value:
        int targetIndex = r.nextInt(target.getLength());
        @Value Object targetValue = target.getType().getCollapsed(targetIndex);

        // Then filter on <= that:
        Filter filter = new Filter(srcTable.mgr, null, srcTable.data.getId(),
            new ComparisonExpression(
                Arrays.asList(
                    new ColumnReference(target.getName(), ColumnReferenceType.CORRESPONDING_ROW),
                    new CallExpression("element", new ColumnReference(target.getName(), ColumnReferenceType.WHOLE_COLUMN), new NumericLiteral(targetIndex + 1 /* User index */, null))
                ), ImmutableList.of(ComparisonOperator.LESS_THAN_OR_EQUAL_TO)));
        for (int row = 0; row < filter.getData().getLength(); row++)
        {
            @Value Object data = filter.getData().getColumn(target.getName()).getType().getCollapsed(row);
            assertTrue("Value " + targetValue + " should be <= " + data + " (but wasn't)", Utility.compareValues(data, targetValue) <= 0);
        }
        //TODO vary comparison operator

        // TODO check the concat with inverted filter is the same data as original
    }
    // #error TODO add test for summary stats
}
