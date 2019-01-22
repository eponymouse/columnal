package test.gui;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.runner.RunWith;
import records.data.CellPosition;
import records.data.ColumnId;
import records.data.TableId;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DataTypeVisitorEx;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.TagType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.NumberInfo;
import records.data.datatype.TypeId;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.gui.MainWindow;
import records.gui.MainWindow.MainWindowActions;
import records.gui.grid.RectangleBounds;
import records.importers.ClipboardUtils;
import records.importers.ClipboardUtils.LoadedColumnInfo;
import records.transformations.SummaryStatistics;
import records.transformations.expression.CallExpression;
import records.transformations.expression.ColumnReference;
import records.transformations.expression.ColumnReference.ColumnReferenceType;
import records.transformations.expression.Expression;
import records.transformations.expression.NumericLiteral;
import records.transformations.expression.StandardFunction;
import records.transformations.expression.TupleExpression;
import records.transformations.function.FunctionList;
import test.TestUtil;
import test.gen.GenRandom;
import test.gen.GenTypeAndValueGen;
import test.gen.GenTypeAndValueGen.TypeAndValueGen;
import test.gui.trait.ClickOnTableHeaderTrait;
import test.gui.trait.CreateDataTableTrait;
import test.gui.trait.EnterExpressionTrait;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FunctionInt;
import utility.Utility;
import utility.Utility.ListExList;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.IntStream;

import static org.junit.Assert.*;

@RunWith(JUnitQuickcheck.class)
@OnThread(Tag.Simulation)
public class TestCreateEditTransformation extends FXApplicationTest implements CreateDataTableTrait, ClickOnTableHeaderTrait, EnterExpressionTrait
{
    private static class AggCalculation
    {
        private final ColumnId columnName;
        private final Expression expression;
        private final DataType dataType;
        // One per split value.  If null, UserException expected
        private final List<@Nullable @Value Object> expectedResult;

        private AggCalculation(ColumnId columnName, Expression expression, DataType dataType, List<@Nullable @Value Object> expectedResult)
        {
            this.columnName = columnName;
            this.expression = expression;
            this.dataType = dataType;
            this.expectedResult = expectedResult;
        }
    }
    
    // One instance per source column
    private static class AggColumns
    {
        private final ColumnDetails sourceColumn;
        private final List<AggCalculation> calculations;

        private AggColumns(ColumnDetails sourceColumn, List<AggCalculation> calculations)
        {
            this.sourceColumn = sourceColumn;
            this.calculations = calculations;
        }
    }
    
    @Property(trials = 4)
    public void testAggregate(@When(seed=8741518136966126489L) @From(GenRandom.class) Random r) throws Exception
    {
        GenTypeAndValueGen genTypeAndValueGen = new GenTypeAndValueGen(false);

        File dest = File.createTempFile("blank", "rec");
        dest.deleteOnExit();
        MainWindowActions mainWindowActions = TestUtil.fx(() -> MainWindow.show(windowToUse, dest, null));
        
        List<ColumnDetails> columns = new ArrayList<>();
        // First add split variable:
        TypeAndValueGen splitType = genTypeAndValueGen.generate(r);
        mainWindowActions._test_getTableManager().getTypeManager()._test_copyTaggedTypesFrom(splitType.getTypeManager());
        List<@Value Object> distinctSplitValues = makeDistinctSortedList(splitType);
        // Now make random duplication count for each:
        List<Integer> replicationCounts = Utility.mapList(distinctSplitValues, _s -> 1 + r.nextInt(10));
        int totalLength = replicationCounts.stream().mapToInt(n -> n).sum();
        columns.add(new ColumnDetails(new ColumnId("Split Col"), splitType.getType(),
            IntStream.range(0, distinctSplitValues.size()).mapToObj(i -> i)
                .<Either<String, @Value Object>>flatMap(i -> Utility.<Either<String, @Value Object>>replicate(replicationCounts.get(i), Either.right(distinctSplitValues.get(i))).stream())
                .collect(ImmutableList.<Either<String, @Value Object>>toImmutableList())));
        
        // Then add source column for aggregate calculations (summing etc):
        List<AggColumns> aggColumns = makeSourceAndCalculations(distinctSplitValues, replicationCounts, genTypeAndValueGen, r);
        for (AggColumns aggColumn : aggColumns)
        {
            columns.add(r.nextInt(columns.size() + 1), aggColumn.sourceColumn);
        } 

        // Add some extra columns with errors just to complicate things:
        int numExtraColumns = r.nextInt(4);
        for (int i = 0; i < numExtraColumns; i++)
        {
            TypeAndValueGen extraType = genTypeAndValueGen.generate(r);
            mainWindowActions._test_getTableManager().getTypeManager()._test_copyTaggedTypesFrom(extraType.getTypeManager());
            columns.add(r.nextInt(columns.size() + 1), new ColumnDetails(new ColumnId("Extra " + i), extraType.getType(), Utility.<Either<String, @Value Object>>replicateM_Ex(totalLength, () -> r.nextInt(10) == 1 ? Either.<String, @Value Object>left("@") : Either.<String, @Value Object>right(extraType.makeValue()))));
        }

        for (ColumnDetails column : columns)
        {
            assertEquals(column.name.getRaw(), totalLength, column.data.size());
        }

        columns = scrambleDataOrder(columns, replicationCounts, r);
        
        createDataTable(mainWindowActions, CellPosition.ORIGIN.offsetByRowCols(1, 1), "Src Data", columns);
        
        // Sanity check the data before proceeding:
        ImmutableList<LoadedColumnInfo> clip = copyTableData(mainWindowActions, "Src Data");

        for (int i = 0; i < clip.size(); i++)
        {
            LoadedColumnInfo copiedColumn = clip.get(i);
            assertEquals(copiedColumn.columnName, columns.get(i).name);
            TestUtil.assertValueListEitherEqual("" + i, columns.get(i).data, copiedColumn.dataValues);
        }
        
        // Now add the actual aggregate:
        CellPosition aggTarget = CellPosition.ORIGIN.offsetByRowCols(1, columns.size() + 2);
        keyboardMoveTo(mainWindowActions._test_getVirtualGrid(), aggTarget);
        clickOnItemInBounds(from(TestUtil.fx(() -> mainWindowActions._test_getVirtualGrid().getNode())), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(aggTarget, aggTarget), MouseButton.PRIMARY);
        TestUtil.delay(100);
        clickOn(".id-new-transform");
        TestUtil.delay(100);
        clickOn(".id-transform-aggregate");
        TestUtil.delay(100);
        write("Src Data");
        push(KeyCode.ENTER);
        TestUtil.sleep(200);
        write("Split Col");
        moveAndDismissPopupsAtPos(point(".ok-button"));
        clickOn(".ok-button");
        TestUtil.sleep(3000);
        
        // Should be one column at the moment, with the distinct split values:
        SummaryStatistics aggTable = (SummaryStatistics)mainWindowActions._test_getTableManager().getAllTables().stream().filter(t -> !t.getId().equals(new TableId("Src Data"))).findFirst().orElseThrow(RuntimeException::new);
        assertEquals(ImmutableList.of(new ColumnId("Split Col")), aggTable.getSplitBy());
        String aggId = aggTable.getId().getRaw();
        ImmutableList<LoadedColumnInfo> initialAgg = copyTableData(mainWindowActions, aggId);
        TestUtil.assertValueListEitherEqual("Table " + aggId, Utility.<@Value Object, Either<String, @Value Object>>mapList(distinctSplitValues, v -> Either.right(v)), initialAgg.get(0).dataValues);
        
        // Now add the calculations:
        int colCount = initialAgg.size();
        for (AggColumns aggColumn : aggColumns)
        {
            for (AggCalculation calculation : aggColumn.calculations)
            {
                CellPosition arrowLoc = aggTarget.offsetByRowCols(2, colCount++);
                keyboardMoveTo(mainWindowActions._test_getVirtualGrid(), arrowLoc);
                clickOnItemInBounds(lookup(".expand-arrow"), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(arrowLoc, arrowLoc));
                // Now enter column name and expression:
                TestUtil.sleep(500);
                checkDialogFocused("New column dialog");
                write(calculation.columnName.getRaw(), 1);
                push(KeyCode.TAB);
                enterExpression(mainWindowActions._test_getTableManager().getTypeManager(), calculation.expression, EntryBracketStatus.SURROUNDED_BY_KEYWORDS, r);
                moveAndDismissPopupsAtPos(point(".ok-button"));
                clickOn(".ok-button");
                TestUtil.sleep(1000);
            }
        }

        ImmutableList<LoadedColumnInfo> finalAgg = copyTableData(mainWindowActions, aggId);
        for (AggColumns aggColumn : aggColumns)
        {
            for (AggCalculation calculation : aggColumn.calculations)
            {
                LoadedColumnInfo calcCol = finalAgg.stream().filter(c -> Objects.equals(c.columnName, calculation.columnName)).findFirst().orElseThrow(() -> new AssertionError("Missing column"));
                
                assertEquals(calculation.columnName.getRaw(), calculation.dataType, calcCol.dataType);
                
                TestUtil.assertValueListEitherEqual(calculation.columnName.getRaw(),
                    calculation.expectedResult.stream().<Either<String, @Value Object>>map(x -> x == null ? Either.<String, @Value Object>left("") : Either.<String, @Value Object>right(x)).collect(ImmutableList.toImmutableList()),
                        calcCol.dataValues
                );
            }
        }
    }

    private List<AggColumns> makeSourceAndCalculations(List<@Value Object> distinctSplitValues, List<Integer> replicationCounts, GenTypeAndValueGen genTypeAndValueGen, Random r) throws UserException, InternalException
    {
        int numColumns = r.nextInt(4);
        int totalLength = replicationCounts.stream().mapToInt(n -> n).sum();
        
        List<AggColumns> aggColumns = new ArrayList<>();
        for (int i = 0; i < numColumns; i++)
        {
            TypeAndValueGen typeAndValueGen = genTypeAndValueGen.generate(r);
            ImmutableList<Either<String, @Value Object>> sourceData = Utility.<Either<String, @Value Object>>replicateM_Ex(totalLength, () -> Either.right(typeAndValueGen.makeValue()));
            ColumnDetails columnDetails = new ColumnDetails(new ColumnId("Source " + i), typeAndValueGen.getType(), sourceData);
            List<AggCalculation> calculations = makeCalculations(typeAndValueGen.getTypeManager().getUnitManager(), columnDetails, replicationCounts, r);
            aggColumns.add(new AggColumns(columnDetails, calculations));
        }
        return aggColumns;
    }

    private List<AggCalculation> makeCalculations(UnitManager unitManager, ColumnDetails columnDetails, List<Integer> replicationCounts, Random random) throws InternalException
    {
        List<List<Either<String, @Value Object>>> groups = new ArrayList<>();
        int start = 0;
        for (Integer count : replicationCounts)
        {
            groups.add(columnDetails.data.subList(start, start + count));
            start += count;
        }
        
        FunctionInt<FunctionInt<List<Either<String, @Value Object>>, @Nullable @Value Object>, List<@Value @Nullable Object>> perGroup = (FunctionInt<List<Either<String, @Value Object>>, @Nullable @Value Object> f) -> {
            ArrayList<@Nullable @Value Object> r = new ArrayList<>();
            for (List<Either<String, @Value Object>> group : groups)
            {
                r.add(f.apply(group));
            }
            return r;
        };


        Function<String, ColumnId> name = s -> new ColumnId(columnDetails.name.getRaw() + " " + s);
        
        return columnDetails.dataType.apply(new DataTypeVisitorEx<List<AggCalculation>, InternalException>()
        {
            private ArrayList<AggCalculation> usual() throws InternalException
            {
                ArrayList<AggCalculation> calculations = new ArrayList<>();
                ColumnReference groupExp = new ColumnReference(columnDetails.name, ColumnReferenceType.WHOLE_COLUMN);
                if (random.nextBoolean())
                {
                    // Keep group as a list:
                    calculations.add(new AggCalculation(name.apply("Group"),
                            groupExp,
                            DataType.array(columnDetails.dataType),
                            perGroup.apply(g -> withEithers(g, ListExList::new))
                    ));
                }
                if (random.nextBoolean())
                {
                    // Minimum:
                    calculations.add(new AggCalculation(name.apply("Min"),
                            new CallExpression(new StandardFunction(TestUtil.checkNonNull(FunctionList.lookup(unitManager, "minimum"))), groupExp),
                            columnDetails.dataType,
                            perGroup.apply(g -> withEithers(g, gr -> gr.stream().min(DataTypeUtility.getValueComparator()).orElse(null)))
                    ));
                }
                if (random.nextBoolean())
                {
                    // Maximum:
                    calculations.add(new AggCalculation(name.apply("Max"),
                            new CallExpression(new StandardFunction(TestUtil.checkNonNull(FunctionList.lookup(unitManager, "maximum"))), groupExp),
                            columnDetails.dataType,
                            perGroup.apply(g -> withEithers(g, gr -> gr.stream().max(DataTypeUtility.getValueComparator()).orElse(null)))
                    ));
                }
                if (random.nextBoolean())
                {
                    // First:
                    calculations.add(new AggCalculation(name.apply("First"),
                            new CallExpression(
                                new StandardFunction(TestUtil.checkNonNull(FunctionList.lookup(unitManager, "element"))), 
                                new TupleExpression(ImmutableList.of(groupExp, new NumericLiteral(1, null)))),
                            columnDetails.dataType,
                            perGroup.apply(g -> withEithers(g, gr -> gr.stream().findFirst().orElse(null)))
                    ));
                }
                // TODO last, sum, join strings
                    //#error TODO also try using the split column
                return calculations;
            }

            private @Nullable @Value Object withEithers(List<Either<String, @Value Object>> src, FunctionInt<List<@Value Object>, @Nullable @Value Object> withList) throws InternalException
            {
                return Either.<String, @Value Object, Either<String, @Value Object>>mapMInt(src, x -> x).<@Nullable @Value Object>mapInt(withList).leftToNull();
            }

            @Override
            public List<AggCalculation> number(NumberInfo numberInfo) throws InternalException
            {
                return usual();
            }

            @Override
            public List<AggCalculation> text() throws InternalException
            {
                return usual();
            }

            @Override
            public List<AggCalculation> date(DateTimeInfo dateTimeInfo) throws InternalException
            {
                return usual();
            }

            @Override
            public List<AggCalculation> bool() throws InternalException
            {
                return usual();
            }

            @Override
            public List<AggCalculation> tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException
            {
                return usual();
            }

            @Override
            public List<AggCalculation> tuple(ImmutableList<DataType> inner) throws InternalException
            {
                return usual();
            }

            @Override
            public List<AggCalculation> array(DataType inner) throws InternalException
            {
                return usual();
            }
        });
    }

    private List<ColumnDetails> scrambleDataOrder(List<ColumnDetails> columns, List<Integer> groupCounts, Random r)
    {
        List<List<Integer>> indexesToDrawFrom = new ArrayList<>();
        int total = 0;
        for (Integer count : groupCounts)
        {
            ArrayList<Integer> sub = new ArrayList<>();
            for (int i = 0; i < count; i++)
            {
                sub.add(total + i);
            }
            indexesToDrawFrom.add(sub);
            total += count;
        }

        List<Integer> oldIndexes = new ArrayList<>();
        for (int i = 0; i < total; i++)
        {
            int group = r.nextInt(indexesToDrawFrom.size());
            int j = indexesToDrawFrom.get(group).remove(0);
            oldIndexes.add(j);
            if (indexesToDrawFrom.get(group).isEmpty())
                indexesToDrawFrom.remove(group);
        }
        
        return Utility.mapList(columns, c -> {
            List<Either<String, @Value Object>> scrambledData = new ArrayList<>();
            for (Integer oldIndex : oldIndexes)
            {
                scrambledData.add(c.data.get(oldIndex));
            }
            return new ColumnDetails(c.name, c.dataType, ImmutableList.copyOf(scrambledData));
        });
    }

    public ImmutableList<LoadedColumnInfo> copyTableData(MainWindowActions mainWindowActions, String tableName) throws UserException
    {
        triggerTableHeaderContextMenu(mainWindowActions._test_getVirtualGrid(), mainWindowActions._test_getTableManager(), new TableId(tableName))
                .clickOn(".id-tableDisplay-menu-copyValues");
        TestUtil.sleep(1000);
        Optional<ImmutableList<LoadedColumnInfo>> clip = TestUtil.<Optional<ImmutableList<LoadedColumnInfo>>>fx(() -> ClipboardUtils.loadValuesFromClipboard(mainWindowActions._test_getTableManager().getTypeManager()));
        assertTrue(clip.isPresent());
        return clip.get();
    }

    // Makes a list containing no duplicate values, using the given type.
    private List<@Value Object> makeDistinctSortedList(TypeAndValueGen splitType) throws UserException, InternalException
    {
        int targetAmount = 12;
        int attempts = 50;
        ArrayList<@Value Object> r = new ArrayList<>();
        nextAttempt: for (int i = 0; i < attempts && r.size() < targetAmount; i++)
        {
            @Value Object newVal = splitType.makeValue();
            // This is O(N^2) but it's only test code, and small N:
            for (@Value Object existing : r)
            {
                if (Utility.compareValues(newVal, existing) == 0)
                    continue nextAttempt;
            }
            r.add(newVal);
        }

        Collections.sort(r, DataTypeUtility.getValueComparator());
        
        return r;
    }
}
