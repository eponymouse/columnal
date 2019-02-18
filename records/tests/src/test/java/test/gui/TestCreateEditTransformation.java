package test.gui;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.stage.Window;
import log.Log;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.runner.RunWith;
import records.data.CellPosition;
import records.data.Column;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.Table;
import records.data.Table.InitialLoadDetails;
import records.data.TableId;
import records.data.TableManager;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DataTypeVisitorEx;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.TagType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.NumberInfo;
import records.data.datatype.TypeId;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.gui.MainWindow;
import records.gui.MainWindow.MainWindowActions;
import records.gui.expressioneditor.AutoComplete;
import records.gui.expressioneditor.AutoComplete.AutoCompleteWindow;
import records.gui.grid.RectangleBounds;
import records.gui.grid.VirtualGrid;
import records.gui.table.HeadedDisplay;
import records.importers.ClipboardUtils;
import records.importers.ClipboardUtils.LoadedColumnInfo;
import records.transformations.Check;
import records.transformations.SummaryStatistics;
import records.transformations.expression.CallExpression;
import records.transformations.expression.ColumnReference;
import records.transformations.expression.ColumnReference.ColumnReferenceType;
import records.transformations.expression.EqualExpression;
import records.transformations.expression.Expression;
import records.transformations.expression.NumericLiteral;
import records.transformations.expression.StandardFunction;
import records.transformations.expression.TupleExpression;
import records.transformations.function.FunctionList;
import test.TestUtil;
import test.gen.GenImmediateData;
import test.gen.GenImmediateData.ImmediateData_Mgr;
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
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;
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

        @OnThread(Tag.Any)
        private AggColumns(ColumnDetails sourceColumn, List<AggCalculation> calculations)
        {
            this.sourceColumn = sourceColumn;
            this.calculations = calculations;
        }
    }
    
    @Property(trials = 3)
    public void testAggregate(@From(GenRandom.class) Random r) throws Exception
    {
        TestUtil.printSeedOnFail(() -> {
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
            List<AggColumns> aggColumns = makeSourceAndCalculations(mainWindowActions._test_getTableManager().getTypeManager(), splitType.getType(), distinctSplitValues, replicationCounts, genTypeAndValueGen, r);
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
            if (r.nextBoolean())
            {
                write("Split Col");
            }
            else
            {
                write("Spl");
                push(KeyCode.ENTER);
                TextField field = getFocusOwner(TextField.class);
                assertEquals("Split Col", TestUtil.fx(() -> field.getText()));
                assertEquals((Integer)"Split Col".length(), TestUtil.<Integer>fx(() -> field.getCaretPosition()));
                MatcherAssert.assertThat(listWindows(), Matchers.everyItem(new BaseMatcher<Window>()
                {
                    @Override
                    public boolean matches(Object o)
                    {
                        return !(o instanceof AutoComplete.AutoCompleteWindow) || !((Window)o).isShowing();
                    }

                    @Override
                    public void describeTo(Description description)
                    {
                        description.appendText("Found showing auto-complete window");
                    }
                }));
            }
            moveAndDismissPopupsAtPos(point(".ok-button"));
            clickOn(".ok-button");
            TestUtil.sleep(3000);

            // Should be one column at the moment, with the distinct split values:
            SummaryStatistics aggTable = (SummaryStatistics) mainWindowActions._test_getTableManager().getAllTables().stream().filter(t -> !t.getId().equals(new TableId("Src Data"))).findFirst().orElseThrow(RuntimeException::new);
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
        });
    }

    private List<AggColumns> makeSourceAndCalculations(TypeManager copyTypesTo, DataType splitColumnType, List<@Value Object> distinctSplitValues, List<Integer> replicationCounts, GenTypeAndValueGen genTypeAndValueGen, Random r) throws UserException, InternalException
    {
        int numColumns = r.nextInt(4);
        int totalLength = replicationCounts.stream().mapToInt(n -> n).sum();
        
        List<AggColumns> aggColumns = new ArrayList<>();
        for (int i = 0; i < numColumns; i++)
        {
            TypeAndValueGen typeAndValueGen = genTypeAndValueGen.generate(r);
            copyTypesTo._test_copyTaggedTypesFrom(typeAndValueGen.getTypeManager());
            ImmutableList<Either<String, @Value Object>> sourceData = Utility.<Either<String, @Value Object>>replicateM_Ex(totalLength, () -> Either.right(typeAndValueGen.makeValue()));
            ColumnDetails columnDetails = new ColumnDetails(new ColumnId("Source " + i), typeAndValueGen.getType(), sourceData);
            List<AggCalculation> calculations = makeCalculations(splitColumnType, distinctSplitValues, typeAndValueGen.getTypeManager().getUnitManager(), columnDetails, replicationCounts, r);
            aggColumns.add(new AggColumns(columnDetails, calculations));
        }
        return aggColumns;
    }

    @OnThread(Tag.Simulation)
    private List<AggCalculation> makeCalculations(DataType splitColumnType, List<@Value Object> distinctSplitValues, UnitManager unitManager, ColumnDetails columnDetails, List<Integer> replicationCounts, Random random) throws InternalException
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

        Comparator<@Value Object> valueComparator = DataTypeUtility.getValueComparator();
        
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
                            new CallExpression(new StandardFunction(TestUtil.checkNonNull(FunctionList.lookup(unitManager, "minimum"))), ImmutableList.of(groupExp)),
                            columnDetails.dataType,
                            perGroup.apply(g -> withEithers(g, gr -> gr.stream().min(valueComparator).orElse(null)))
                    ));
                }
                if (random.nextBoolean())
                {
                    // Maximum:
                    calculations.add(new AggCalculation(name.apply("Max"),
                            new CallExpression(new StandardFunction(TestUtil.checkNonNull(FunctionList.lookup(unitManager, "maximum"))), ImmutableList.of(groupExp)),
                            columnDetails.dataType,
                            perGroup.apply(g -> withEithers(g, gr -> gr.stream().max(valueComparator).orElse(null)))
                    ));
                }
                if (random.nextBoolean())
                {
                    // First:
                    calculations.add(new AggCalculation(name.apply("First"),
                            new CallExpression(
                                new StandardFunction(TestUtil.checkNonNull(FunctionList.lookup(unitManager, "element"))), 
                                ImmutableList.of(groupExp, new NumericLiteral(1, null))),
                            columnDetails.dataType,
                            perGroup.apply(g -> withEithers(g, gr -> gr.stream().findFirst().orElse(null)))
                    ));
                }
                if (columnDetails.dataType.isNumber())
                {
                    calculations.add(new AggCalculation(name.apply("Sum"),
                        new CallExpression(
                            new StandardFunction(TestUtil.checkNonNull(FunctionList.lookup(unitManager, "sum"))),
                            ImmutableList.of(groupExp)),
                        columnDetails.dataType,
                        perGroup.apply(g -> withEithers(g, gr -> gr.stream().reduce((a, b) -> Utility.addSubtractNumbers((Number)a, (Number)b, true)).orElse(null)))
                    ));
                }
                if (columnDetails.dataType.isText())
                {
                    calculations.add(new AggCalculation(name.apply("Concat"),
                            new CallExpression(
                                    new StandardFunction(TestUtil.checkNonNull(FunctionList.lookup(unitManager, "join text"))),
                                    ImmutableList.of(groupExp)),
                            columnDetails.dataType,
                            perGroup.apply(g -> withEithers(g, gr -> DataTypeUtility.value(gr.stream().map(s -> (String)s).collect(Collectors.joining("")))))
                    ));
                }
                if (random.nextBoolean())
                {
                    calculations.add(new AggCalculation(name.apply("Split"),
                        new ColumnReference(new ColumnId("Split Col"), ColumnReferenceType.CORRESPONDING_ROW),
                        splitColumnType,
                            distinctSplitValues.stream().<@Nullable @Value Object>map(x -> x).collect(Collectors.<@Nullable @Value Object>toList())
                    ));
                }
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
    
    @Property(trials = 5)
    public void testCheck(@From(GenImmediateData.class) ImmediateData_Mgr srcImmedData, @From(GenRandom.class) Random r) throws Exception
    {
        MainWindowActions details = TestUtil.openDataAsTable(windowToUse, srcImmedData.mgr).get();
        TestUtil.sleep(1000);
        TableManager tableManager = details._test_getTableManager();
        VirtualGrid virtualGrid = details._test_getVirtualGrid();
        List<Table> allTables = tableManager.getAllTables();
        
        Table srcTable = allTables.get(r.nextInt(allTables.size()));
        RecordSet srcData = srcTable.getData();
        List<Column> srcColumns = srcData.getColumns();
        Column srcColumn = srcColumns.get(r.nextInt(srcColumns.size()));
        
        @Value Object thresholdItem = srcColumn.getType().getCollapsed(r.nextInt(srcColumn.getLength()));
        
        @SuppressWarnings("nullness")
        CellPosition targetPos = allTables.stream().map(t -> TestUtil.fx(() -> ((HeadedDisplay)t.getDisplay()).getBottomRightIncl()).offsetByRowCols(1, 0)).max(Comparator.comparing(p -> p.columnIndex)).get();
        
        keyboardMoveTo(virtualGrid, targetPos);
        TestUtil.delay(100);
        
        Log.debug("Aiming for " + targetPos);
        clickOnItemInBounds(from(TestUtil.fx(() -> virtualGrid.getNode())), virtualGrid, new RectangleBounds(targetPos, targetPos));

        TestUtil.delay(100);
        clickOn(".id-new-check");
        TestUtil.delay(100);
        write(srcTable.getId().getRaw());
        push(KeyCode.ENTER);

        EqualExpression checkExpression = new EqualExpression(ImmutableList.of(new ColumnReference(srcColumn.getName(), ColumnReferenceType.CORRESPONDING_ROW), Expression.parse(null, DataTypeUtility.valueToString(srcColumn.getType(), thresholdItem, null, true), tableManager.getTypeManager())));
        
        // Enter expression:
        enterExpression(tableManager.getTypeManager(), checkExpression, EntryBracketStatus.SURROUNDED_BY_KEYWORDS, r);

        moveAndDismissPopupsAtPos(point(".ok-button"));
        clickOn(".ok-button");

        Label label = (Label)withItemInBounds(lookup(".label"), virtualGrid, new RectangleBounds(targetPos, targetPos.offsetByRowCols(1, 0)), (n, p) -> {});
        
        assertEquals("Pass", TestUtil.fx(() -> label.getText()));
    }
}
