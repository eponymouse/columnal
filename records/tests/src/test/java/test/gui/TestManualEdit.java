package test.gui;

import annotation.qual.Value;
import annotation.units.TableDataColIndex;
import annotation.units.TableDataRowIndex;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import log.Log;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.hamcrest.MatcherAssert;
import org.junit.runner.RunWith;
import records.data.CellPosition;
import records.data.Column;
import records.data.ColumnId;
import records.data.EditableColumn;
import records.data.KnownLengthRecordSet;
import records.data.RecordSet;
import records.data.Table;
import records.data.Table.TableDisplayBase;
import records.data.TableId;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeUtility.ComparableValue;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UserException;
import records.gui.DataCellSupplier.VersionedSTF;
import records.gui.MainWindow.MainWindowActions;
import records.gui.grid.RectangleBounds;
import records.gui.table.TableDisplay;
import records.importers.ClipboardUtils;
import records.importers.ClipboardUtils.LoadedColumnInfo;
import records.transformations.ManualEdit;
import records.transformations.Sort;
import records.transformations.Sort.Direction;
import test.TestUtil;
import test.gen.GenRandom;
import test.gen.type.GenDataTypeMaker;
import test.gen.type.GenDataTypeMaker.DataTypeAndValueMaker;
import test.gui.trait.ClickTableLocationTrait;
import test.gui.trait.EnterStructuredValueTrait;
import test.gui.trait.ListUtilTrait;
import test.gui.trait.PopupTrait;
import test.gui.trait.ScrollToTrait;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ComparableEither;
import utility.Either;
import utility.ExSupplier;
import utility.Pair;
import utility.SimulationFunction;
import utility.Utility;

import java.math.BigDecimal;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.TreeMap;
import java.util.function.Supplier;

import static org.junit.Assert.*;

@RunWith(JUnitQuickcheck.class)
public class TestManualEdit extends FXApplicationTest implements ListUtilTrait, ScrollToTrait, PopupTrait, ClickTableLocationTrait, EnterStructuredValueTrait
{
    @Property(trials = 10)
    @OnThread(Tag.Simulation)
    public void propManualEdit(
            @When(seed=1L) @From(GenDataTypeMaker.class) GenDataTypeMaker.DataTypeMaker typeMaker,
            @When(seed=1L) @From(GenRandom.class) Random r) throws Exception
    {
        int length = 1 + r.nextInt(50);
        int numColumns = 1 + r.nextInt(5);
        List<DataTypeAndValueMaker> columnTypes = Utility.replicateM_Ex(numColumns, () -> typeMaker.makeType());
        List<SimulationFunction<RecordSet, EditableColumn>> makeColumns = Utility.<DataTypeAndValueMaker, SimulationFunction<RecordSet, EditableColumn>>mapListEx(columnTypes, columnType -> {
            ColumnId columnId = TestUtil.generateColumnId(new SourceOfRandomness(r));

            ImmutableList<Either<String, @Value Object>> values = Utility.<Either<String, @Value Object>>replicateM_Ex(length, () -> {
                if (r.nextInt(5) == 1)
                {
                    return Either.left("@" + r.nextInt());
                }
                else
                {
                    return Either.right(columnType.makeValue());
                }
            });
            
            return columnType.getDataType().makeImmediateColumn(columnId, values, columnType.makeValue());
        });
        RecordSet srcRS = new KnownLengthRecordSet(makeColumns, length);
        
        // Save the table, then open GUI and load it, then add a filter transformation (rename to keeprows)
        MainWindowActions mainWindowActions = TestUtil.openDataAsTable(windowToUse, typeMaker.getTypeManager(), srcRS);
        TestUtil.sleep(5000);
        // Add a sort transformation:
        CellPosition targetSortPos = TestUtil.fx(() -> mainWindowActions._test_getTableManager().getNextInsertPosition(null));
        keyboardMoveTo(mainWindowActions._test_getVirtualGrid(), targetSortPos);
        clickOnItemInBounds(from(TestUtil.fx(() -> mainWindowActions._test_getVirtualGrid().getNode())), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(targetSortPos, targetSortPos), MouseButton.PRIMARY);
        TestUtil.delay(100);
        clickOn(".id-new-transform");
        TestUtil.delay(100);
        clickOn(".id-transform-sort");
        TestUtil.delay(100);
        // Select the single listed table:
        push(KeyCode.DOWN);
        push(KeyCode.ENTER);
        ColumnId sortBy = srcRS.getColumnIds().get(r.nextInt(numColumns));
        write(sortBy.getRaw());
        moveAndDismissPopupsAtPos(point(".ok-button"));
        clickOn();
        
        // The ID is fixed but the table is not, so we use ID 
        TableId sortId = mainWindowActions._test_getTableManager().getAllTables().stream().filter(t -> t instanceof Sort).findFirst().orElseThrow(() -> new RuntimeException("No edit")).getId();
        
        ExSupplier<Sort> findFirstSort = () -> (Sort)mainWindowActions._test_getTableManager().getSingleTableOrThrow(sortId);
        assertEquals(ImmutableList.of(new Pair<>(sortBy, Direction.ASCENDING)), findFirstSort.get().getSortBy());

        // Null means use row number
        @Nullable Column replaceKeyColumn = r.nextInt(5) == 1 ? null : findFirstSort.get().getData().getColumns().get(r.nextInt(numColumns));
        
        @SuppressWarnings("units")
        Supplier<@TableDataRowIndex Integer> makeRowIndex = () -> r.nextInt(length);
        @SuppressWarnings("units")
        Supplier<@TableDataColIndex Integer> makeColIndex = () -> r.nextInt(numColumns);

        HashMap<ColumnId, TreeMap<ComparableValue, ComparableEither<String, ComparableValue>>> replacementsSoFar = new HashMap<>();

        
        // There's two ways to create a new manual edit.  One is to just create it using the menu.  The other is to try to edit an existing transformation, and follow the popup which appears
        if (r.nextBoolean())
        {
            @TableDataRowIndex int row = makeRowIndex.get();
            @TableDataColIndex int col = makeColIndex.get();

            @Nullable @Value Object replaceKey;
            if (replaceKeyColumn == null)
                replaceKey = DataTypeUtility.value(new BigDecimal(row));
            else
                replaceKey = TestUtil.getSingleCollapsedData(replaceKeyColumn.getType(), row).leftToNull();

            @Value Object value = columnTypes.get(col).makeValue();
            if (replaceKey != null)
            {
                replacementsSoFar.computeIfAbsent(findFirstSort.get().getData().getColumnIds().get(col), k -> new TreeMap<>())
                        .put(new ComparableValue(replaceKey), ComparableEither.right(new ComparableValue(value)));
            }
            else
            {
                // Trying to edit row with error key, so
                // not clear what should happen.  Just leave it...
                return;
            }
            
            keyboardMoveTo(mainWindowActions._test_getVirtualGrid(), mainWindowActions._test_getTableManager(), sortId, row, col);
            push(KeyCode.ENTER);

            enterStructuredValue(columnTypes.get(col).getDataType(), value, r, false);
            push(KeyCode.ENTER);
            
            assertTrue("Alert should be showing asking whether to create manual edit", lookup(".alert").tryQuery().isPresent());
            clickOn(".yes-button");
            sleep(500);
            assertFalse("Alert should be dismissed", lookup(".alert").tryQuery().isPresent());
            // Now fall through to fill in same details as creating directly...
        }
        else
        {
            // Let's create it directly.
            CellPosition targetPos = TestUtil.fx(() -> mainWindowActions._test_getTableManager().getNextInsertPosition(null));


            keyboardMoveTo(mainWindowActions._test_getVirtualGrid(), targetPos);
            clickOnItemInBounds(from(TestUtil.fx(() -> mainWindowActions._test_getVirtualGrid().getNode())), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(targetPos, targetPos), MouseButton.PRIMARY);
            TestUtil.delay(100);
            clickOn(".id-new-transform");
            TestUtil.delay(100);
            clickOn(".id-transform-edit");
            TestUtil.delay(100);
            write(sortId.getRaw());
            push(KeyCode.ENTER);
            TestUtil.delay(1000);
        }
        
        if (replaceKeyColumn == null)
            clickOn(".id-manual-edit-byrownum");
        else
        {
            clickOn(".id-manual-edit-bycolumn");
            push(KeyCode.TAB);
            write(replaceKeyColumn.getName().getRaw());
        }
        clickOn(".ok-button");
        sleep(1000);

        TableId manualEditId = mainWindowActions._test_getTableManager().getAllTables().stream().filter(t -> t instanceof ManualEdit).findFirst().orElseThrow(() -> new RuntimeException("No edit")).getId();
        ExSupplier<ManualEdit> findManualEdit = () -> (ManualEdit) mainWindowActions._test_getTableManager().getSingleTableOrThrow(manualEditId);
        TableDisplay manualEditDisplay = TestUtil.checkNonNull((TableDisplay)TestUtil.<@Nullable TableDisplayBase>fx(() -> findManualEdit.get().getDisplay()));

        // Add a second sort transformation, sorting the edit:
        targetSortPos = TestUtil.fx(() -> mainWindowActions._test_getTableManager().getNextInsertPosition(manualEditId));
        keyboardMoveTo(mainWindowActions._test_getVirtualGrid(), targetSortPos);
        clickOnItemInBounds(from(TestUtil.fx(() -> mainWindowActions._test_getVirtualGrid().getNode())), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(targetSortPos, targetSortPos), MouseButton.PRIMARY);
        TestUtil.delay(100);
        clickOn(".id-new-transform");
        TestUtil.delay(100);
        clickOn(".id-transform-sort");
        TestUtil.delay(100);
        write(manualEditId.getRaw());
        push(KeyCode.ENTER);
        ColumnId secondSortBy = srcRS.getColumnIds().get(r.nextInt(numColumns));
        write(secondSortBy.getRaw());
        moveAndDismissPopupsAtPos(point(".ok-button"));
        clickOn();
        
        // Now make the further manual edits:
        int numFurtherEdits = r.nextInt(10);
        for (int i = 0; i < numFurtherEdits; i++)
        {
            @TableDataRowIndex int row = makeRowIndex.get();
            @TableDataColIndex int col = makeColIndex.get();
            
            @Nullable @Value Object replaceKey;
            if (replaceKeyColumn == null)
                replaceKey = DataTypeUtility.value(new BigDecimal(row));
            else
                replaceKey = TestUtil.getSingleCollapsedData(replaceKeyColumn.getType(), row).leftToNull();
            
            @Value Object value = columnTypes.get(col).makeValue();
            if (replaceKey != null)
            {
                replacementsSoFar.computeIfAbsent(findFirstSort.get().getData().getColumnIds().get(col), k -> new TreeMap<>())
                        .put(new ComparableValue(replaceKey), ComparableEither.right(new ComparableValue(value)));
            }

            assertFalse("Alert should not be showing yet", lookup(".alert").tryQuery().isPresent());
            keyboardMoveTo(mainWindowActions._test_getVirtualGrid(), mainWindowActions._test_getTableManager(), manualEditId, row, col);
            sleep(500);
            assertFalse("Alert should not be showing yet", lookup(".alert").tryQuery().isPresent());
            push(KeyCode.ENTER);
            // If key has an error, we should get a dialog.
            if (replaceKey == null)
            {
                sleep(500);
                assertTrue("Alert should be showing about invalid key", lookup(".alert").tryQuery().isPresent());
                clickOn(".ok-button");
                assertFalse("Alert still showing after OK", lookup(".alert").tryQuery().isPresent());
            }
            else
            {
                assertFalse("False alert showing, but valid key", lookup(".alert").tryQuery().isPresent());
                enterStructuredValue(columnTypes.get(col).getDataType(), value, r, false);
                push(KeyCode.ENTER);
            }
        }
        
        if (r.nextInt(3) == 1)
        {
            // Change sort order of source:
            clickOn(".edit-sort-by");
            sleep(200);
            clickOn(".small-delete");
            sleep(200);
            clickOn(".id-fancylist-add");
            sortBy = srcRS.getColumnIds().get(r.nextInt(numColumns));
            write(sortBy.getRaw());
            clickOn(".ok-button");
            sleep(1000);
        }
        if (r.nextBoolean())
        {
            // Change original data:
            int numChanges = r.nextInt(10);
            for (int i = 0; i < numChanges; i++)
            {
                @TableDataRowIndex int row = makeRowIndex.get();
                @TableDataColIndex int col = makeColIndex.get();
                @Value Object value = columnTypes.get(col).makeValue();
                keyboardMoveTo(mainWindowActions._test_getVirtualGrid(), mainWindowActions._test_getTableManager(), findFirstSort.get().getSrcTableId(), row, col);
                sleep(500);
                assertFalse("Alert should not be showing", lookup(".alert").tryQuery().isPresent());
                push(KeyCode.ENTER);
                enterStructuredValue(columnTypes.get(col).getDataType(), value, r, false);
                push(KeyCode.ENTER);
            }
            sleep(1000);
        }
        
        // Now check output values by getting them from clipboard:
        TestUtil.sleep(500);
        keyboardMoveTo(mainWindowActions._test_getVirtualGrid(), manualEditDisplay.getMostRecentPosition());
        showContextMenu(withItemInBounds(lookup(".table-display-table-title.transformation-table-title"), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(manualEditDisplay.getMostRecentPosition(), manualEditDisplay.getMostRecentPosition()), (n, p) -> {}), null)
                .clickOn(".id-tableDisplay-menu-copyValues");
        TestUtil.sleep(2000);

        Optional<ImmutableList<LoadedColumnInfo>> editViaClip = TestUtil.<Optional<ImmutableList<LoadedColumnInfo>>>fx(() -> ClipboardUtils.loadValuesFromClipboard(mainWindowActions._test_getTableManager().getTypeManager()));
        assertTrue(editViaClip.isPresent());
        ImmutableList<LoadedColumnInfo> expected = makeExpected(findFirstSort.get().getData(), replaceKeyColumn == null ? null : replaceKeyColumn.getName(), replacementsSoFar, sortBy);
        
        assertEquals(replacementsSoFar, findManualEdit.get()._test_getReplacements());
        checkEqual(expected, editViaClip.get());
        checkEqual(expected, getGraphicalContent(mainWindowActions, findManualEdit.get()));
        
        // Copy the values from the resulting sort:
        Sort secondSort = mainWindowActions._test_getTableManager().getAllTables().stream().filter(t -> t instanceof Sort && !t.getId().equals(sortId)).map(t -> (Sort)t).findFirst().orElseThrow(() -> new RuntimeException("No edit"));
        TableDisplay secondSortDisplay = (TableDisplay) TestUtil.checkNonNull(TestUtil.<@Nullable TableDisplayBase>fx(() -> secondSort.getDisplay()));
        keyboardMoveTo(mainWindowActions._test_getVirtualGrid(), secondSortDisplay.getMostRecentPosition());
        showContextMenu(withItemInBounds(lookup(".table-display-table-title.transformation-table-title"), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(secondSortDisplay.getMostRecentPosition(), secondSortDisplay.getMostRecentPosition()), (n, p) -> {}), null)
                .clickOn(".id-tableDisplay-menu-copyValues");
        TestUtil.sleep(1000);

        Optional<ImmutableList<LoadedColumnInfo>> secondSortViaClip = TestUtil.<Optional<ImmutableList<LoadedColumnInfo>>>fx(() -> ClipboardUtils.loadValuesFromClipboard(mainWindowActions._test_getTableManager().getTypeManager()));
        assertTrue(secondSortViaClip.isPresent());
        ImmutableList<LoadedColumnInfo> expectedSecondSort = makeExpected(findManualEdit.get().getData(), replaceKeyColumn == null ? null : replaceKeyColumn.getName(), replacementsSoFar, secondSortBy);

        checkEqual(expectedSecondSort, secondSortViaClip.get());
        checkEqual(expectedSecondSort, getGraphicalContent(mainWindowActions, secondSort));
    }

    @OnThread(Tag.Simulation)
    private ImmutableList<LoadedColumnInfo> makeExpected(RecordSet original, @Nullable ColumnId replacementIdentifier, HashMap<ColumnId, TreeMap<ComparableValue, ComparableEither<String, ComparableValue>>> replacements, @Nullable ColumnId sortBy) throws UserException, InternalException
    {
        int length = original.getLength();
        // Maps original indexes to sorted indexes
        ArrayList<Integer> sortMap = new ArrayList<>(new AbstractList<Integer>()
        {
            @Override
            public Integer get(int index)
            {
                return index;
            }

            @Override
            public int size()
            {
                return length;
            }
        });

        if (sortBy != null)
        {
            DataTypeValue sortByColumn = original.getColumn(sortBy).getType();
            Collections.sort(sortMap, Comparator.<Integer, ComparableEither<String, ComparableValue>>comparing(i -> {
                try
                {
                    return ComparableEither.fromEither(TestUtil.getSingleCollapsedData(sortByColumn, i).map(ComparableValue::new));
                }
                catch (InternalException | UserException e)
                {
                    throw new RuntimeException(e);
                }
            }));
        }
        
        return Utility.mapListExI(original.getColumns(), column -> {
            TreeMap<ComparableValue, ComparableEither<String, ComparableValue>> colReplacements = replacements.getOrDefault(column.getName(), new TreeMap<>());
            ImmutableList.Builder<Either<String, @Value Object>> columnValues = ImmutableList.builderWithExpectedSize(original.getLength());
            for (int rowWithoutSort = 0; rowWithoutSort < original.getLength(); rowWithoutSort++)
            {
                // We must map backwards to original row:
                int rowWithoutSortFinal = rowWithoutSort;
                int row = sortMap.get(rowWithoutSort);//Utility.findFirstIndex(sortMap, i -> i == rowWithoutSortFinal).orElseThrow(() -> new RuntimeException("Invalid sortMap"));
                Either<String, @Value Object> rowKey = replacementIdentifier == null ? Either.<String, @Value Object>right(DataTypeUtility.value(row)) : TestUtil.getSingleCollapsedData(original.getColumn(replacementIdentifier).getType(), row);
                @Nullable ComparableEither<String, ComparableValue> replacement = rowKey.<@Nullable ComparableEither<String, ComparableValue>>either(err -> null, k -> colReplacements.get(new ComparableValue(k)));
                if (replacement != null)
                    columnValues.add(replacement.<@Value Object>map(r -> r.getValue()));
                else
                    columnValues.add(TestUtil.getSingleCollapsedData(column.getType(), row));
                    
            }
            
            return new LoadedColumnInfo(column.getName(), column.getType().getType(), columnValues.build());
        });
    }

    @OnThread(Tag.Simulation)
    private void checkEqual(ImmutableList<LoadedColumnInfo> expected, ImmutableList<LoadedColumnInfo> actual) throws InternalException, UserException
    {
        assertEquals(expected.size(), actual.size());
        for (int colIndex = 0; colIndex < expected.size(); colIndex++)
        {
            LoadedColumnInfo expCol = expected.get(colIndex);
            LoadedColumnInfo actCol = actual.get(colIndex);
            
            assertEquals(expCol.columnName, actCol.columnName);
            assertEquals(expCol.dataType, actCol.dataType);
            TestUtil.assertValueListEitherEqual("Column " + expCol.columnName + " (" + colIndex + ")", expCol.dataValues, actCol.dataValues);
        }
    }

    @OnThread(Tag.Simulation)
    @SuppressWarnings("units")
    private ImmutableList<LoadedColumnInfo> getGraphicalContent(MainWindowActions mainWindowActions, Table table) throws InternalException, UserException
    {
        RecordSet tableData = table.getData();
        return Utility.mapListExI_Index(tableData.getColumns(), (colIndex, column) -> {
            TableDisplay tableDisplay = TestUtil.checkNonNull(TestUtil.<@Nullable TableDisplay>fx(() -> (TableDisplay) table.getDisplay()));
            ImmutableList.Builder<Either<String, @Value Object>> values = ImmutableList.builder();
            for (int row = 0; row < tableData.getLength(); row++)
            {
                int rowFinal = row;
                Either<String, @Value Object> internalContent = TestUtil.getSingleCollapsedData(column.getType(), rowFinal);
                values.add(TestUtil.checkNonNull(TestUtil.<@Nullable Either<String, @Value Object>>fx(() -> {
                    VersionedSTF cell = mainWindowActions._test_getDataCell(tableDisplay._test_getDataPosition(rowFinal, colIndex));
                    if (cell == null)
                        return null;
                    String content = cell._test_getGraphicalText();
                    // Bit of a hack, but need to deal with truncated numbers, so we get direct content if truncated:
                    if (column.getType().getType().isNumber() && content.contains("\u2026"))
                        return internalContent;
                    return column.getType().getType().loadSingleItem(content);
                })));
            }
            
            return new LoadedColumnInfo(column.getName(), column.getType().getType(), values.build());
        });
    }
}
