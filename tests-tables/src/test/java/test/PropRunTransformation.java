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

import annotation.qual.Value;
import annotation.units.TableDataRowIndex;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.runner.RunWith;
import test.functions.TFunctionUtil;
import xyz.columnal.data.Column;
import xyz.columnal.id.ColumnId;
import xyz.columnal.data.TBasicUtil;
import xyz.columnal.data.RecordSet;
import xyz.columnal.id.TableId;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.DataTypeValue;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.Aggregate;
import xyz.columnal.transformations.Concatenate;
import xyz.columnal.transformations.Concatenate.IncompleteColumnHandling;
import xyz.columnal.transformations.Filter;
import xyz.columnal.transformations.HideColumns;
import xyz.columnal.transformations.Sort;
import xyz.columnal.transformations.Sort.Direction;
import xyz.columnal.transformations.Calculate;
import xyz.columnal.transformations.expression.CallExpression;
import xyz.columnal.transformations.expression.ComparisonExpression;
import xyz.columnal.transformations.expression.ComparisonExpression.ComparisonOperator;
import xyz.columnal.transformations.expression.Expression;
import xyz.columnal.transformations.expression.IdentExpression;
import xyz.columnal.transformations.expression.NumericLiteral;
import xyz.columnal.transformations.function.FunctionList;
import test.gen.GenImmediateData;
import test.gen.GenRandom;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.IdentifierUtility;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.Utility;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Random;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

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
@SuppressWarnings("recorded")
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

        Sort sort = new Sort(srcTable.mgr, TFunctionUtil.ILD, srcTable.data().getId(), ImmutableList.of(new Pair<>(sortBy.getName(), direction)));

        // TODO sort by multiple columns, too

        assertTrue("Sorting by " + sortBy.getName() + " " + direction + ":\n" + src.debugGetVals(), !TTableUtil.streamFlattened(sort.getData().getColumn(sortBy.getName()))
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
        Assert.assertEquals(TTableUtil.getRowFreq(src), TTableUtil.getRowFreq(sort.getData()));
    }

    @Property
    @OnThread(Tag.Simulation)
    public void testFilter(@From(GenImmediateData.class) GenImmediateData.ImmediateData_Mgr srcTable, @From(GenRandom.class) Random r) throws UserException, InternalException
    {
        // Pick a column to filter on:
        List<Column> columns = srcTable.data().getData().getColumns();
        int targetColumnIndex = r.nextInt(columns.size());
        Column target = columns.get(targetColumnIndex);

        assumeTrue(target.getLength() > 0);

        // Pick a random value:
        int targetRowIndex = r.nextInt(target.getLength());
        @Value Object targetValue = target.getType().getCollapsed(targetRowIndex);

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
        Filter filter = new Filter(srcTable.mgr, TFunctionUtil.ILD, srcTable.data().getId(),
            new ComparisonExpression(
                Arrays.asList(
                    IdentExpression.column(target.getName()),
                    new CallExpression(FunctionList.getFunctionLookup(srcTable.mgr.getUnitManager()), "element", IdentExpression.makeEntireColumnReference(srcTable.data().getId(), target.getName()), new NumericLiteral(targetRowIndex + 1 /* User index */, null))
                ), ImmutableList.of(op)));
        srcTable.mgr.record(filter);
        for (int row = 0; row < filter.getData().getLength(); row++)
        {
            @Value Object data = filter.getData().getColumn(target.getName()).getType().getCollapsed(row);
            assertTrue("Value " + targetValue + " should be " + op.saveOp()+ " " + data + " (but wasn't)", check.test(Utility.compareValues(data, targetValue)));
        }
        

        Filter invertedFilter = new Filter(srcTable.mgr, TFunctionUtil.ILD, srcTable.data().getId(),
            new ComparisonExpression(
                Arrays.asList(
                    IdentExpression.column(target.getName()),
                    new CallExpression(FunctionList.getFunctionLookup(srcTable.mgr.getUnitManager()), "element", IdentExpression.makeEntireColumnReference(srcTable.data().getId(), target.getName()), new NumericLiteral(targetRowIndex + 1 /* User index */, null))
                ), ImmutableList.of(invert(op))));
        srcTable.mgr.record(invertedFilter);
        
        // Lengths should equal original:
        @TableDataRowIndex int filterLength = filter.getData().getLength();
        @TableDataRowIndex int invertedFilterLength = invertedFilter.getData().getLength();
        Assert.assertEquals(srcTable.data().getData().getLength(), filterLength + invertedFilterLength);

        

        boolean includeSourceColumn = r.nextBoolean();
        Concatenate concatFilters = new Concatenate(srcTable.mgr, TFunctionUtil.ILD, ImmutableList.of(filter.getId(), invertedFilter.getId()), IncompleteColumnHandling.DEFAULT, includeSourceColumn);

        List<ColumnId> srcColNames = srcTable.data().getData().getColumnIds();
        if (includeSourceColumn)
            srcColNames = Utility.prependToList(new ColumnId("Source"), srcColNames);
        // Should be same column names in same order, modulo the extra Source column:
        assertEquals(srcColNames, concatFilters.getData().getColumnIds());;

        // Check that the same set of rows is present:
        Stream<List<@Value Object>> srcWithSrc = TTableUtil.streamFlattened(srcTable.data().getData()).<List<@Value Object>>map(p -> {
            if (!includeSourceColumn)
                return p.getSecond();
            else
                return TBasicUtil.<List<@Value Object>>checkedToRuntime(() -> {
                    boolean matchesFilter = check.test(Utility.compareValues(p.getSecond().get(targetColumnIndex), targetValue));
                    return Utility.prependToList(DataTypeUtility.value(matchesFilter ? filter.getId().getRaw() : invertedFilter.getId().getRaw()), p.getSecond());
                });
        });
        ImmutableList<Entry<List<@Value Object>, Long>> srcData = TTableUtil.getRowFreq(srcWithSrc).entrySet().stream().collect(ImmutableList.toImmutableList());
        ImmutableList<Entry<List<@Value Object>, Long>> destData = TTableUtil.getRowFreq(concatFilters.getData()).entrySet().stream().collect(ImmutableList.toImmutableList());
        assertEquals(srcData.size(), destData.size());
        for (int i = 0; i < srcData.size(); i++)
        {
            TBasicUtil.assertValueListEqual("Row " + i, srcData.get(i).getKey(), destData.get(i).getKey());
            assertEquals("Row " + i, srcData.get(i).getValue(), destData.get(i).getValue());
        }
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
                return DataTypeUtility.isNumber(c.getType().getType());
            } catch (InternalException | UserException e)
            {
                throw new RuntimeException(e);
            }
        }).findFirst();
        assumeTrue(numericColumn.isPresent());

        Expression countExpression = new CallExpression(FunctionList.getFunctionLookup(original.mgr.getUnitManager()), "list length", IdentExpression.makeEntireColumnReference(original.data().getId(), original.data().getData().getColumns().get(0).getName()));
        Aggregate aggregate = new Aggregate(original.mgr, TFunctionUtil.ILD, original.data().getId(), ImmutableList.of(new Pair<>(new ColumnId("COUNT"), countExpression)), ImmutableList.of());
        RecordSet summaryRS = aggregate.getData();
        assertEquals(1, summaryRS.getLength());
        assertEquals(1, summaryRS.getColumns().size());
        Assert.assertEquals(original.data().getData().getLength(), DataTypeUtility.requireInteger(summaryRS.getColumns().get(0).getType().getCollapsed(0)));

        Expression sumExpression = new CallExpression(FunctionList.getFunctionLookup(original.mgr.getUnitManager()), "sum", IdentExpression.makeEntireColumnReference(original.data().getId(), numericColumn.get().getName()));
        aggregate = new Aggregate(original.mgr, TFunctionUtil.ILD, original.data().getId(), ImmutableList.of(new Pair<>(new ColumnId("SUM"), sumExpression)), ImmutableList.of());
        summaryRS = aggregate.getData();
        assertEquals(1, summaryRS.getLength());
        assertEquals(1, summaryRS.getColumns().size());
        MatcherAssert.assertThat(TTableUtil.toString(numericColumn.get()), Utility.toBigDecimal(Utility.valueNumber(summaryRS.getColumns().get(0).getType().getCollapsed(0))), comparesEqualTo(bdSum(numericColumn.get().getLength(), numericColumn.get().getType())));
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
    public void testConcatSelf(@From(GenImmediateData.class) GenImmediateData.ImmediateData_Mgr original, boolean includeSource) throws InternalException, UserException
    {
        List<TableId> ids = new ArrayList<>();
        @TableDataRowIndex int originalLength = original.data().getData().getLength();
        for (int repeats = 1; repeats < 4; repeats++)
        {
            // Add once each time round the loop:
            ids.add(original.data().getId());
            Concatenate concatenate = new Concatenate(original.mgr, TFunctionUtil.ILD, ImmutableList.copyOf(ids), IncompleteColumnHandling.DEFAULT, includeSource);
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
            HideColumns hidden = new HideColumns(original.mgr, TFunctionUtil.ILD, original.data().getId(), ImmutableList.of());
            assertEquals(expected, hidden.getData().getColumnIds());
            assertDataSame(hidden.getData(), original.data().getData(), Function.identity());
        }

        // Test one:
        for (ColumnId single : originalIds)
        {
            List<ColumnId> expected = new ArrayList<>(originalIds);
            expected.remove(single);
            HideColumns hidden = new HideColumns(original.mgr, TFunctionUtil.ILD, original.data().getId(), ImmutableList.of(single));
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
        ImmutableMap.Builder<ColumnId, Expression> newCols = ImmutableMap.builder();
        List<Column> oldColumns = original.data().getData().getColumns();
        Map<ColumnId, ColumnId> columnMapping = new HashMap<>();

        for (Column oldColumn : oldColumns)
        {
            columnMapping.put(oldColumn.getName(), oldColumn.getName());
        }
        for (int i = 0; i < numNew; i++)
        {
            ColumnId old = oldColumns.get(r.nextInt(oldColumns.size())).getName();
            ColumnId newId = new ColumnId(IdentifierUtility.identNum("Trans", i));
            newCols.put(newId, IdentExpression.column(old));
            columnMapping.put(newId, old);
        }


        Calculate calculate = new Calculate(original.mgr, TFunctionUtil.ILD, original.data().getId(), newCols.build());

        assertDataSame(calculate.getData(), original.data().getData(), c -> columnMapping.getOrDefault(c, new ColumnId("TEST_UNKNOWN")));
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
            new HideColumns(original.mgr, TFunctionUtil.ILD, original.data().getId(), ImmutableList.copyOf(original.data().getData().getColumnIds())).getData();
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
                new HideColumns(original.mgr, TFunctionUtil.ILD, original.data().getId(), ImmutableList.copyOf(toRemove)).getData();
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
