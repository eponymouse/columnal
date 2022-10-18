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

package test.gui.transformation;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.stage.Window;
import test.functions.TFunctionUtil;
import test.gui.TAppUtil;
import test.gui.TFXUtil;
import xyz.columnal.log.Log;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.runner.RunWith;
import xyz.columnal.data.CellPosition;
import xyz.columnal.data.Column;
import xyz.columnal.id.ColumnId;
import xyz.columnal.data.TBasicUtil;
import xyz.columnal.data.RecordSet;
import xyz.columnal.data.Table;
import xyz.columnal.id.TableId;
import xyz.columnal.data.TableManager;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataType.DataTypeVisitorEx;
import xyz.columnal.data.datatype.DataType.DateTimeInfo;
import xyz.columnal.data.datatype.DataType.TagType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.NumberInfo;
import xyz.columnal.data.datatype.TypeId;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.data.unit.UnitManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.gui.MainWindow;
import xyz.columnal.gui.MainWindow.MainWindowActions;
import xyz.columnal.gui.dialog.AutoComplete;
import xyz.columnal.gui.grid.RectangleBounds;
import xyz.columnal.gui.grid.VirtualGrid;
import xyz.columnal.gui.table.HeadedDisplay;
import xyz.columnal.importers.ClipboardUtils;
import xyz.columnal.importers.ClipboardUtils.LoadedColumnInfo;
import xyz.columnal.transformations.Aggregate;
import xyz.columnal.transformations.Check.CheckType;
import xyz.columnal.transformations.expression.CallExpression;
import xyz.columnal.transformations.expression.EqualExpression;
import xyz.columnal.transformations.expression.Expression;
import xyz.columnal.transformations.expression.IdentExpression;
import xyz.columnal.transformations.expression.ImplicitLambdaArg;
import xyz.columnal.transformations.expression.NumericLiteral;
import xyz.columnal.transformations.function.FunctionList;
import test.gen.GenImmediateData;
import test.gen.GenImmediateData.ImmediateData_Mgr;
import test.gen.GenRandom;
import test.gen.type.GenTypeAndValueGen;
import test.gen.type.GenTypeAndValueGen.TypeAndValueGen;
import test.gui.trait.ClickOnTableHeaderTrait;
import test.gui.trait.ComboUtilTrait;
import test.gui.trait.CreateDataTableTrait;
import test.gui.trait.EnterExpressionTrait;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.FunctionInt;
import xyz.columnal.utility.IdentifierUtility;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Utility.ListExList;

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
public class TestCreateEditTransformation extends FXApplicationTest implements CreateDataTableTrait, ClickOnTableHeaderTrait, EnterExpressionTrait, ComboUtilTrait
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
        TBasicUtil.printSeedOnFail(() -> {
            GenTypeAndValueGen genTypeAndValueGen = new GenTypeAndValueGen(false);

            File dest = File.createTempFile("blank", "rec");
            dest.deleteOnExit();
            MainWindowActions mainWindowActions = TFXUtil.fx(() -> MainWindow.show(windowToUse, dest, null, null));

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
                columns.add(r.nextInt(columns.size() + 1), new ColumnDetails(new ColumnId(IdentifierUtility.identNum("Extra", i)), extraType.getType(), Utility.<Either<String, @Value Object>>replicateM_Ex(totalLength, () -> r.nextInt(10) == 1 ? Either.<String, @Value Object>left("@") : Either.<String, @Value Object>right(extraType.makeValue()))));
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
                TBasicUtil.assertValueListEitherEqual("" + i, columns.get(i).data, copiedColumn.dataValues);
            }

            // Now add the actual aggregate:
            CellPosition aggTarget = CellPosition.ORIGIN.offsetByRowCols(1, columns.size() + 2);
            keyboardMoveTo(mainWindowActions._test_getVirtualGrid(), aggTarget);
            clickOnItemInBounds(fromNode(TFXUtil.fx(() -> mainWindowActions._test_getVirtualGrid().getNode())), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(aggTarget, aggTarget), MouseButton.PRIMARY);
            TFXUtil.sleep(100);
            clickOn(".id-new-transform");
            TFXUtil.sleep(100);
            clickOn(".id-transform-aggregate");
            TFXUtil.sleep(100);
            write("Src Data");
            push(KeyCode.ENTER);
            sleep(300);
            write(aggColumns.get(0).calculations.get(0).columnName.getRaw(), 1);
            push(KeyCode.TAB);
            enterExpression(mainWindowActions._test_getTableManager().getTypeManager(), aggColumns.get(0).calculations.get(0).expression, EntryBracketStatus.SURROUNDED_BY_KEYWORDS, r);
            moveAndDismissPopupsAtPos(point(".id-agg-split-columns"));
            clickOn(".id-agg-split-columns");
            sleep(300);
            clickOn(".id-fancylist-add");
            if (r.nextBoolean())
            {
                write("Split Col");
            }
            else
            {
                write("Spl");
                push(KeyCode.DOWN);
                push(KeyCode.ENTER);
                TextField field = getFocusOwner(TextField.class);
                assertEquals("Split Col", TFXUtil.fx(() -> field.getText()));
                assertEquals((Integer)"Split Col".length(), TFXUtil.<Integer>fx(() -> field.getCaretPosition()));
                MatcherAssert.assertThat(TFXUtil.fx(() -> listWindows()), Matchers.everyItem(new BaseMatcher<Window>()
                {
                    @Override
                    public boolean matches(Object o)
                    {
                        return !(o instanceof AutoComplete.AutoCompleteWindow) || TFXUtil.fx(() -> !((Window)o).isShowing());
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
            TFXUtil.sleep(3000);

            // Should be one column at the moment, with the distinct split values, and maybe the first calculation:
            Aggregate aggTable = (Aggregate) mainWindowActions._test_getTableManager().getAllTables().stream().filter(t -> !t.getId().equals(new TableId("Src Data"))).findFirst().orElseThrow(RuntimeException::new);
            assertEquals(ImmutableList.of(new ColumnId("Split Col")), aggTable.getSplitBy());
            @ExpressionIdentifier String aggId = aggTable.getId().getRaw();
            ImmutableList<LoadedColumnInfo> initialAgg = copyTableData(mainWindowActions, aggId);
            //TestUtil.assertValueListEitherEqual("Table " + aggId, Utility.<@Value Object, Either<String, @Value Object>>mapList(distinctSplitValues, v -> Either.right(v)), initialAgg.get(0).dataValues);

            // Now add the calculations:
            int colCount = initialAgg.size();
            boolean skipFirst = true;
            for (AggColumns aggColumn : aggColumns)
            {
                for (AggCalculation calculation : aggColumn.calculations)
                {
                    if (skipFirst)
                    {
                        skipFirst = false;
                        continue;
                    }
                    CellPosition arrowLoc = aggTarget.offsetByRowCols(2, colCount++);
                    keyboardMoveTo(mainWindowActions._test_getVirtualGrid(), arrowLoc);
                    clickOnItemInBounds(".expand-arrow", mainWindowActions._test_getVirtualGrid(), new RectangleBounds(arrowLoc, arrowLoc));
                    // Now enter column name and expression:
                    TFXUtil.sleep(500);
                    checkDialogFocused("New column dialog");
                    write(calculation.columnName.getRaw(), 1);
                    push(KeyCode.TAB);
                    enterExpression(mainWindowActions._test_getTableManager().getTypeManager(), calculation.expression, EntryBracketStatus.SURROUNDED_BY_KEYWORDS, r);
                    moveAndDismissPopupsAtPos(point(".ok-button"));
                    clickOn(".ok-button");
                    TFXUtil.sleep(1000);
                }
            }

            ImmutableList<LoadedColumnInfo> finalAgg = copyTableData(mainWindowActions, aggId);
            for (AggColumns aggColumn : aggColumns)
            {
                for (AggCalculation calculation : aggColumn.calculations)
                {
                    LoadedColumnInfo calcCol = finalAgg.stream().filter(c -> Objects.equals(c.columnName, calculation.columnName)).findFirst().orElseThrow(() -> new AssertionError("Missing column"));

                    assertEquals(calculation.columnName.getRaw(), calculation.dataType, calcCol.dataType);

                    TBasicUtil.assertValueListEitherEqual(calculation.columnName.getRaw(),
                            calculation.expectedResult.stream().<Either<String, @Value Object>>map(x -> x == null ? Either.<String, @Value Object>left("") : Either.<String, @Value Object>right(x)).collect(ImmutableList.toImmutableList()),
                            calcCol.dataValues
                    );
                }
            }
        });
    }

    private List<AggColumns> makeSourceAndCalculations(TypeManager copyTypesTo, DataType splitColumnType, List<@Value Object> distinctSplitValues, List<Integer> replicationCounts, GenTypeAndValueGen genTypeAndValueGen, Random r) throws UserException, InternalException
    {
        int numColumns = 1 + r.nextInt(4);
        int totalLength = replicationCounts.stream().mapToInt(n -> n).sum();
        
        List<AggColumns> aggColumns = new ArrayList<>();
        for (int i = 0; i < numColumns; i++)
        {
            TypeAndValueGen typeAndValueGen = genTypeAndValueGen.generate(r);
            copyTypesTo._test_copyTaggedTypesFrom(typeAndValueGen.getTypeManager());
            ImmutableList<Either<String, @Value Object>> sourceData = Utility.<Either<String, @Value Object>>replicateM_Ex(totalLength, () -> Either.right(typeAndValueGen.makeValue()));
            ColumnDetails columnDetails = new ColumnDetails(new ColumnId(IdentifierUtility.identNum("Source", i)), typeAndValueGen.getType(), sourceData);
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


        @SuppressWarnings("identifier")
        Function<String, ColumnId> name = s -> new ColumnId(columnDetails.name.getRaw() + " " + s);

        Comparator<@Value Object> valueComparator = DataTypeUtility.getValueComparator();
        
        return columnDetails.dataType.apply(new DataTypeVisitorEx<List<AggCalculation>, InternalException>()
        {
            private ArrayList<AggCalculation> usual() throws InternalException
            {
                ArrayList<AggCalculation> calculations = new ArrayList<>();
                Expression groupExp = IdentExpression.column(columnDetails.name);
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
                            new CallExpression(IdentExpression.function(TBasicUtil.checkNonNull(FunctionList.lookup(unitManager, "minimum")).getFullName()), ImmutableList.of(groupExp)),
                            columnDetails.dataType,
                            perGroup.apply(g -> withEithers(g, gr -> gr.stream().min(valueComparator).orElse(null)))
                    ));
                }
                if (random.nextBoolean())
                {
                    // Maximum:
                    calculations.add(new AggCalculation(name.apply("Max"),
                            new CallExpression(IdentExpression.function(TBasicUtil.checkNonNull(FunctionList.lookup(unitManager, "maximum")).getFullName()), ImmutableList.of(groupExp)),
                            columnDetails.dataType,
                            perGroup.apply(g -> withEithers(g, gr -> gr.stream().max(valueComparator).orElse(null)))
                    ));
                }
                if (random.nextBoolean())
                {
                    // First:
                    calculations.add(new AggCalculation(name.apply("First"),
                            new CallExpression(
                                IdentExpression.function(TBasicUtil.checkNonNull(FunctionList.lookup(unitManager, "element")).getFullName()), 
                                ImmutableList.of(groupExp, new NumericLiteral(1, null))),
                            columnDetails.dataType,
                            perGroup.apply(g -> withEithers(g, gr -> gr.stream().findFirst().orElse(null)))
                    ));
                }
                if (DataTypeUtility.isNumber(columnDetails.dataType))
                {
                    calculations.add(new AggCalculation(name.apply("Sum"),
                        new CallExpression(
                            IdentExpression.function(TBasicUtil.checkNonNull(FunctionList.lookup(unitManager, "sum")).getFullName()),
                            ImmutableList.of(groupExp)),
                        columnDetails.dataType,
                        perGroup.apply(g -> withEithers(g, gr -> gr.stream().reduce((a, b) -> Utility.addSubtractNumbers((Number)a, (Number)b, true)).orElse(null)))
                    ));
                }
                if (columnDetails.dataType.equals(DataType.TEXT))
                {
                    calculations.add(new AggCalculation(name.apply("Concat"),
                            new CallExpression(
                                    IdentExpression.function(TBasicUtil.checkNonNull(FunctionList.lookup(unitManager, "join text")).getFullName()),
                                    ImmutableList.of(groupExp)),
                            columnDetails.dataType,
                            perGroup.apply(g -> withEithers(g, gr -> DataTypeUtility.value(gr.stream().map(s -> (String)s).collect(Collectors.joining("")))))
                    ));
                }
                if (random.nextBoolean())
                {
                    calculations.add(new AggCalculation(name.apply("Split"),
                        IdentExpression.column(new ColumnId("Split Col")),
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
            public List<AggCalculation> record(ImmutableMap<@ExpressionIdentifier String, DataType> fields) throws InternalException, InternalException
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

    public ImmutableList<LoadedColumnInfo> copyTableData(MainWindowActions mainWindowActions, @ExpressionIdentifier String tableName) throws UserException
    {
        triggerTableHeaderContextMenu(mainWindowActions._test_getVirtualGrid(), mainWindowActions._test_getTableManager(), new TableId(tableName))
                .clickOn(".id-tableDisplay-menu-copyValues");
        TFXUtil.sleep(2000);
        Optional<ImmutableList<LoadedColumnInfo>> clip = TFXUtil.<Optional<ImmutableList<LoadedColumnInfo>>>fx(() -> ClipboardUtils.loadValuesFromClipboard(mainWindowActions._test_getTableManager().getTypeManager()));
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
        MainWindowActions details = TAppUtil.openDataAsTable(windowToUse, srcImmedData.mgr).get();
        TFXUtil.sleep(1000);
        TableManager tableManager = details._test_getTableManager();
        VirtualGrid virtualGrid = details._test_getVirtualGrid();
        List<Table> allTables = tableManager.getAllTables();
        
        Table srcTable = allTables.get(r.nextInt(allTables.size()));
        RecordSet srcData = srcTable.getData();
        List<Column> srcColumns = srcData.getColumns();
        Column srcColumn = srcColumns.get(r.nextInt(srcColumns.size()));
        
        // Standalone is randomly substituted for some of these later on:
        ImmutableList<CheckType> possibleTypes = ImmutableList.of(CheckType.ALL_ROWS, CheckType.NO_ROWS, CheckType.ALL_ROWS);
        CheckType checkType = possibleTypes.get(r.nextInt(possibleTypes.size()));
        if (srcColumn.getLength() == 0)
            return;
        
        @Value Object containedItem = srcColumn.getType().getCollapsed(r.nextInt(srcColumn.getLength()));
        boolean allEqualToContained = true;
        for (int row = 0; row < srcColumn.getLength(); row++)
        {
            if (Utility.compareValues(containedItem, srcColumn.getType().getCollapsed(row)) != 0)
                allEqualToContained = false;
        }
        
        final Expression checkExpression;
        final boolean expectedPass = checkType == CheckType.ANY_ROW || (checkType == CheckType.ALL_ROWS && allEqualToContained);
        Expression containedItemExpression = TFunctionUtil.parseExpression(DataTypeUtility.valueToString(containedItem, srcColumn.getType().getType(), false, null), tableManager.getTypeManager(), FunctionList.getFunctionLookup(tableManager.getUnitManager()));
        if (r.nextInt(3) == 0)
        {
            // Fake it using a stand-alone check
            String function;
            if (checkType == CheckType.ALL_ROWS)
            {
                function = "all";
            }
            else if (checkType == CheckType.NO_ROWS)
            {
                function = "none";
            }
            else
            {
                function = "any";
            }
                
            checkExpression = new CallExpression(
                FunctionList.getFunctionLookup(tableManager.getUnitManager()), function,
                    IdentExpression.makeEntireColumnReference(srcTable.getId(), srcColumn.getName()),
                    new EqualExpression(ImmutableList.of(new ImplicitLambdaArg(), containedItemExpression), false));
            
            checkType = CheckType.STANDALONE;
        }
        else
        {
            checkExpression = new EqualExpression(ImmutableList.of(IdentExpression.column(srcColumn.getName()), containedItemExpression), false);
        }

        @SuppressWarnings("nullness")
        CellPosition targetPos = allTables.stream().map(t -> TFXUtil.fx(() -> ((HeadedDisplay)t.getDisplay()).getBottomRightIncl()).offsetByRowCols(8, 2)).max(Comparator.comparing(p -> p.columnIndex)).get();

        keyboardMoveTo(virtualGrid, targetPos);
        TFXUtil.sleep(300);

        Log.debug("Aiming for " + targetPos);
        clickOnItemInBounds(fromNode(TFXUtil.fx(() -> virtualGrid.getNode())), virtualGrid, new RectangleBounds(targetPos, targetPos));

        TFXUtil.sleep(300);
        clickOn(".id-new-check");
        TFXUtil.sleep(300);
        write(srcTable.getId().getRaw());
        push(KeyCode.ENTER);

        selectGivenComboBoxItem(waitForOne(".check-type-combo"), checkType);

        push(KeyCode.TAB);
        
        // Enter expression:
        enterExpression(tableManager.getTypeManager(), checkExpression, EntryBracketStatus.SURROUNDED_BY_KEYWORDS, r);

        moveAndDismissPopupsAtPos(point(".ok-button"));
        clickOn(".ok-button");
        
        sleep(500);

        CellPosition labelPos = targetPos.offsetByRowCols(1, 0);
        Label label = (Label)withItemInBounds(".check-result", virtualGrid, new RectangleBounds(targetPos, labelPos), (n, p) -> {});
        
        assertEquals(expectedPass ? "OK" : "Fail", TFXUtil.fx(() -> label.getText()));

        if (!expectedPass)
        {
            // Test out the clicking to see the explanation:
            keyboardMoveTo(virtualGrid, labelPos);
            assertNotShowing("Explanation", ".explanation-flow");
            if (r.nextBoolean())
                clickOn(label);
            else
                push(KeyCode.ENTER);
            sleep(500);
            assertShowing("Explanation", ".explanation-flow");
        }
    }
}
