package test;

import annotation.qual.Value;
import annotation.units.TableDataRowIndex;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.generator.Precision;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.junit.Assume;
import org.junit.runner.RunWith;
import records.data.Column;
import records.data.ColumnId;
import records.data.ImmediateDataSource;
import records.data.RecordSet;
import records.data.TableId;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.Concatenate;
import records.transformations.Concatenate.IncompleteColumnHandling;
import records.transformations.Filter;
import records.transformations.HideColumns;
import records.transformations.Sort;
import records.transformations.Sort.Direction;
import records.transformations.SummaryStatistics;
import records.transformations.Transform;
import records.transformations.expression.CallExpression;
import records.transformations.expression.ColumnReference;
import records.transformations.expression.ColumnReference.ColumnReferenceType;
import records.transformations.expression.ComparisonExpression;
import records.transformations.expression.ComparisonExpression.ComparisonOperator;
import records.transformations.expression.Expression;
import records.transformations.expression.NumericLiteral;
import test.gen.GenImmediateData;
import test.gen.GenImmediateData.NumTables;
import test.gen.GenRandom;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.hamcrest.Matchers.comparesEqualTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeThat;
import static org.junit.Assume.assumeTrue;

/**
 * Created by neil on 01/12/2016.
 */
@SuppressWarnings({"deprecation", "recorded"})
@RunWith(JUnitQuickcheck.class)
public class PropRunTransformation
{
    @Property
    @OnThread(Tag.Simulation)
    public void testSort(@From(GenImmediateData.class) GenImmediateData.ImmediateData_Mgr srcTable, @From(GenRandom.class) Random r) throws UserException, InternalException
    {
        // Pick an arbitrary column and sort by it
        RecordSet src = srcTable.data().getData();
        Column sortBy = src.getColumns().get(r.nextInt(src.getColumns().size()));
        Direction direction = r.nextBoolean() ? Direction.ASCENDING : Direction.DESCENDING;

        Sort sort = new Sort(srcTable.mgr, TestUtil.ILD, srcTable.data().getId(), ImmutableList.of(new Pair<>(sortBy.getName(), direction)));

        // TODO sort by multiple columns, too

        assertTrue("Sorting by " + sortBy.getName() + " " + direction + ":\n" + src.debugGetVals(), !TestUtil.streamFlattened(sort.getData().getColumn(sortBy.getName()))
            .pairMap((a, b) ->
            {
                try
                {
                    int cmp = Utility.compareValues(a, b);
                    // Flip if descending:
                    if (direction == Direction.DESCENDING)
                        cmp = -cmp;
                    
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
        List<Column> columns = srcTable.data().getData().getColumns();
        Column target = columns.get(r.nextInt(columns.size()));

        assumeTrue(target.getLength() > 0);

        // Pick a random value:
        int targetIndex = r.nextInt(target.getLength());
        @Value Object targetValue = target.getType().getCollapsed(targetIndex);

        ComparisonOperator op;
        Predicate<Integer> check;
        switch (r.nextInt(4))
        {
            case 0:
                op = ComparisonOperator.GREATER_THAN;
                check = cmp -> cmp > 0;
                break;
            case 1:
                op = ComparisonOperator.GREATER_THAN_OR_EQUAL_TO;
                check = cmp -> cmp >= 0;
                break;
            case 2:
                op = ComparisonOperator.LESS_THAN;
                check = cmp -> cmp < 0;
                break;
            default:
                op = ComparisonOperator.LESS_THAN_OR_EQUAL_TO;
                check = cmp -> cmp <= 0;
                break;
        }

        // Then filter on <= that:
        Filter filter = new Filter(srcTable.mgr, TestUtil.ILD, srcTable.data().getId(),
            new ComparisonExpression(
                Arrays.asList(
                    new ColumnReference(target.getName(), ColumnReferenceType.CORRESPONDING_ROW),
                    new CallExpression(srcTable.mgr.getUnitManager(), "element", new ColumnReference(target.getName(), ColumnReferenceType.WHOLE_COLUMN), new NumericLiteral(targetIndex + 1 /* User index */, null))
                ), ImmutableList.of(op)));
        for (int row = 0; row < filter.getData().getLength(); row++)
        {
            @Value Object data = filter.getData().getColumn(target.getName()).getType().getCollapsed(row);
            assertTrue("Value " + targetValue + " should be " + op.saveOp()+ " " + data + " (but wasn't)", check.test(Utility.compareValues(data, targetValue)));
        }
        srcTable.mgr.record(filter);

        Filter invertedFilter = new Filter(srcTable.mgr, TestUtil.ILD, srcTable.data().getId(),
            new ComparisonExpression(
                Arrays.asList(
                    new ColumnReference(target.getName(), ColumnReferenceType.CORRESPONDING_ROW),
                    new CallExpression(srcTable.mgr.getUnitManager(), "element", new ColumnReference(target.getName(), ColumnReferenceType.WHOLE_COLUMN), new NumericLiteral(targetIndex + 1 /* User index */, null))
                ), ImmutableList.of(invert(op))));

        // Lengths should equal original:
        @TableDataRowIndex int filterLength = filter.getData().getLength();
        @TableDataRowIndex int invertedFilterLength = invertedFilter.getData().getLength();
        assertEquals(srcTable.data().getData().getLength(), filterLength + invertedFilterLength);

        srcTable.mgr.record(invertedFilter);
        
        Concatenate concatFilters = new Concatenate(srcTable.mgr, TestUtil.ILD, ImmutableList.of(filter.getId(), invertedFilter.getId()), IncompleteColumnHandling.DEFAULT);

        // Check that the same set of rows is present:
        assertEquals(TestUtil.getRowFreq(srcTable.data().getData()), TestUtil.getRowFreq(concatFilters.getData()));
    }

    private ComparisonOperator invert(ComparisonOperator op)
    {
        switch (op)
        {
            case LESS_THAN:
                return ComparisonOperator.GREATER_THAN_OR_EQUAL_TO;
            case LESS_THAN_OR_EQUAL_TO:
                return ComparisonOperator.GREATER_THAN;
            case GREATER_THAN:
                return ComparisonOperator.LESS_THAN_OR_EQUAL_TO;
            case GREATER_THAN_OR_EQUAL_TO:
                return ComparisonOperator.LESS_THAN;
        }
        throw new RuntimeException("Missing case for inverting comparison op");
    }

    @Property
    @OnThread(Tag.Simulation)
    public void testSummaryStats(@From(GenImmediateData.class) GenImmediateData.ImmediateData_Mgr original) throws InternalException, UserException
    {
        Optional<Column> numericColumn = original.data().getData().getColumns().stream().filter(c ->
        {
            try
            {
                return c.getType().isNumber();
            } catch (InternalException | UserException e)
            {
                throw new RuntimeException(e);
            }
        }).findFirst();
        assumeTrue(numericColumn.isPresent());

        Expression countExpression = new CallExpression(original.mgr.getUnitManager(), "count", new ColumnReference(original.data().getData().getColumns().get(0).getName(), ColumnReferenceType.WHOLE_COLUMN));
        SummaryStatistics summaryStatistics = new SummaryStatistics(original.mgr, TestUtil.ILD, original.data().getId(), ImmutableList.of(new Pair<>(new ColumnId("COUNT"), countExpression)), ImmutableList.of());
        RecordSet summaryRS = summaryStatistics.getData();
        assertEquals(1, summaryRS.getLength());
        assertEquals(1, summaryRS.getColumns().size());
        assertEquals(original.data().getData().getLength(), DataTypeUtility.requireInteger(summaryRS.getColumns().get(0).getType().getCollapsed(0)));

        Expression sumExpression = new CallExpression(original.mgr.getUnitManager(),"sum", new ColumnReference(numericColumn.get().getName(), ColumnReferenceType.WHOLE_COLUMN));
        summaryStatistics = new SummaryStatistics(original.mgr, TestUtil.ILD, original.data().getId(), ImmutableList.of(new Pair<>(new ColumnId("SUM"), sumExpression)), ImmutableList.of());
        summaryRS = summaryStatistics.getData();
        assertEquals(1, summaryRS.getLength());
        assertEquals(1, summaryRS.getColumns().size());
        assertThat(TestUtil.toString(numericColumn.get()), Utility.toBigDecimal(Utility.valueNumber(summaryRS.getColumns().get(0).getType().getCollapsed(0))), comparesEqualTo(bdSum(numericColumn.get().getLength(), numericColumn.get().getType())));
    }

    @OnThread(Tag.Simulation)
    private BigDecimal bdSum(int length, DataTypeValue type) throws UserException, InternalException
    {
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < length; i++)
            total = total.add(Utility.toBigDecimal(Utility.valueNumber(type.getCollapsed(i))), MathContext.DECIMAL128);
        return total;
    }

    @Property
    @OnThread(Tag.Simulation)
    public void testConcatSelf(@From(GenImmediateData.class) GenImmediateData.ImmediateData_Mgr original) throws InternalException, UserException
    {
        List<TableId> ids = new ArrayList<>();
        @TableDataRowIndex int originalLength = original.data().getData().getLength();
        for (int repeats = 1; repeats < 4; repeats++)
        {
            // Add once each time round the loop:
            ids.add(original.data().getId());
            Concatenate concatenate = new Concatenate(original.mgr, TestUtil.ILD, ImmutableList.copyOf(ids), IncompleteColumnHandling.DEFAULT);
            for (Column column : concatenate.getData().getColumns())
            {
                // Compare each value from the original set with the corresponding later repeated values:
                for (int originalRow = 0; originalRow < originalLength; originalRow++)
                {
                    // This will compare to self, but why not:
                    for (int repeatCompare = 0; repeatCompare < repeats; repeatCompare++)
                    {
                        assertEquals(0, Utility.compareValues(column.getType().getCollapsed(originalRow), column.getType().getCollapsed(originalRow + repeatCompare * originalLength)));
                    }
                }

            }
        }
    }

    /*
    @Property
    @SuppressWarnings("nullness")
    @OnThread(Tag.Simulation)
    public void testConcatUnrelated(@From(GenImmediateData.class) @NumTables(minTables = 2, maxTables = 2) GenImmediateData.ImmediateData_Mgr data) throws InternalException, UserException
    {
        // Test that concating two tables with different columns with insufficient defaults
        // will fail:
        List<ColumnId> sharedIds = new ArrayList<>(data.data().getData().getColumnIds());
        sharedIds.retainAll(data.data.get(1).getData().getColumnIds());
        Assume.assumeTrue(sharedIds.isEmpty());
        Assume.assumeTrue(data.data().getData().getLength() > 0);
        Assume.assumeTrue(data.data.get(1).getData().getLength() > 0);

        try
        {
            new Concatenate(data.mgr, null, ImmutableList.of(data.data().getId(), data.data.get(1).getId()), IncompleteColumnHandling.DEFAULT).getData();
            fail("Expected failure concatting two unrelated tables");
        }
        catch (UserException e)
        {
            // Expected
        }

        // Test it does work with enough missing values:
        Map<ColumnId, Pair<DataType, Optional<@Value Object>>> missing = new HashMap<>();

        for (ImmediateDataSource table : data.data)
        {
            for (ColumnId id : table.getData().getColumnIds())
            {
                missing.put(id, new Pair<>(table.getData().getColumn(id).getType(), Optional.<@Value Object>of(table.getData().getColumn(id).getType().getCollapsed(0))));
            }
        }
        RecordSet concat = new Concatenate(data.mgr, null, ImmutableList.of(data.data().getId(), data.data.get(1).getId()), missing).getData();
        assertEquals(data.data().getData().getLength() + data.data.get(1).getData().getLength(), concat.getLength());
        for (ColumnId c : concat.getColumnIds())
        {
            // First check all of the ones that correspond to A:
            for (int i = 0; i < data.data().getData().getLength(); i++)
            {
                // If it was in A, check value
                if (data.data().getData().getColumnIds().contains(c))
                {
                    assertEquals(0, Utility.compareValues(data.data().getData().getColumn(c).getType().getCollapsed(i), concat.getColumn(c).getType().getCollapsed(i)));
                }
                else
                {
                    // Otherwise it must be the default:
                    assertEquals(0, Utility.compareValues(missing.get(c).getSecond().get(), concat.getColumn(c).getType().getCollapsed(i)));
                }
            }

            // Then check B:
            for (int i = data.data().getData().getLength(); i < data.data().getData().getLength() + data.data.get(1).getData().getLength(); i++)
            {
                // If it was in A, check value
                if (data.data.get(1).getData().getColumnIds().contains(c))
                {
                    assertEquals(0, Utility.compareValues(data.data.get(1).getData().getColumn(c).getType().getCollapsed(i - data.data().getData().getLength()), concat.getColumn(c).getType().getCollapsed(i)));
                }
                else
                {
                    // Otherwise it must be the default:
                    assertEquals(0, Utility.compareValues(missing.get(c).getSecond().get(), concat.getColumn(c).getType().getCollapsed(i)));
                }
            }
        }

        //#error TODO test it works with overlapping same-typed columns (may need transform to rename columns)
        // TODO test it fails with overlapping columns of different types
    }
    */
    // TODO test more concat failure cases

    @Property
    @OnThread(Tag.Simulation)
    public void testSuccessfulHide(@From(GenImmediateData.class) GenImmediateData.ImmediateData_Mgr original) throws InternalException, UserException
    {
        List<ColumnId> originalIds = original.data().getData().getColumnIds();

        assumeTrue(originalIds.size() > 1);

        // Test zero:
        {
            List<ColumnId> expected = new ArrayList<>(originalIds);
            HideColumns hidden = new HideColumns(original.mgr, TestUtil.ILD, original.data().getId(), ImmutableList.of());
            assertEquals(expected, hidden.getData().getColumnIds());
            assertDataSame(hidden.getData(), original.data().getData(), Function.identity());
        }

        // Test one:
        for (ColumnId single : originalIds)
        {
            List<ColumnId> expected = new ArrayList<>(originalIds);
            expected.remove(single);
            HideColumns hidden = new HideColumns(original.mgr, TestUtil.ILD, original.data().getId(), ImmutableList.of(single));
            assertEquals(expected, hidden.getData().getColumnIds());
            assertDataSame(hidden.getData(), original.data().getData(), Function.identity());
        }
    }

    @Property
    @OnThread(Tag.Simulation)
    public void testTransformAsRename(@From(GenImmediateData.class) GenImmediateData.ImmediateData_Mgr original, @From(GenRandom.class) Random r) throws InternalException, UserException
    {
        // Pick a few new destination columns:
        int numNew = r.nextInt(5);
        ImmutableList.Builder<Pair<ColumnId, Expression>> newCols = ImmutableList.builder();
        List<Column> oldColumns = original.data().getData().getColumns();
        Map<ColumnId, ColumnId> columnMapping = new HashMap<>();

        for (Column oldColumn : oldColumns)
        {
            columnMapping.put(oldColumn.getName(), oldColumn.getName());
        }
        for (int i = 0; i < numNew; i++)
        {
            ColumnId old = oldColumns.get(r.nextInt(oldColumns.size())).getName();
            ColumnId newId = new ColumnId("Trans" + i);
            newCols.add(new Pair<>(newId, new ColumnReference(old, ColumnReferenceType.CORRESPONDING_ROW)));
            columnMapping.put(newId, old);
        }


        Transform transform = new Transform(original.mgr, TestUtil.ILD, original.data().getId(), newCols.build());

        assertDataSame(transform.getData(), original.data().getData(), c -> columnMapping.getOrDefault(c, new ColumnId("__TEST_UNKNOWN__")));
    }

    // Checks that all columns from first parameter have same data as
    // their counterpart in second parameter, where counterpart
    // is determined by applying the function to map the column name
    // Note this is not symmetrical between the two data sets!
    // Any columns that are in second param but not first are ignored.
    @OnThread(Tag.Simulation)
    private void assertDataSame(RecordSet smaller, RecordSet bigger, Function<ColumnId, ColumnId> smallToBig) throws UserException, InternalException
    {
        for (Column a : smaller.getColumns())
        {
            Column b = bigger.getColumn(smallToBig.apply(a.getName()));
            assertEquals(a.getLength(), b.getLength());
            for (int i = 0; i < a.getLength(); i++)
            {
                assertEquals(0, Utility.compareValues(a.getType().getCollapsed(i), b.getType().getCollapsed(i)));
            }
        }
    }

    @Property
    @OnThread(Tag.Simulation)
    public void testUnsuccessfulHide(@From(GenImmediateData.class) GenImmediateData.ImmediateData_Mgr original) throws InternalException, UserException
    {
        // Hiding all should be invalid:
        try
        {
            new HideColumns(original.mgr, TestUtil.ILD, original.data().getId(), ImmutableList.copyOf(original.data().getData().getColumnIds())).getData();
            fail("Hide all");
        }
        catch (UserException e)
        {
            // As expected
        }

        // Hiding a column which doesn't feature (perhaps alongside legit columns):
        ArrayList<ColumnId> toRemove = new ArrayList<>();
        toRemove.add(new ColumnId("hdgsufdhggggiosfsgdf"));
        for (ColumnId c : original.data().getData().getColumnIds())
        {
            try
            {
                new HideColumns(original.mgr, TestUtil.ILD, original.data().getId(), ImmutableList.copyOf(toRemove)).getData();
                fail("Hide non-existing");
            }
            catch (UserException e)
            {
                // As expected
            }
            toRemove.add(new Random().nextInt(toRemove.size()), c);
        }
    }
}
