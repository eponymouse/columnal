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
import utility.Pair;
import utility.SimulationFunction;
import utility.Utility;

import java.math.BigDecimal;
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

        Sort sort = mainWindowActions._test_getTableManager().getAllTables().stream().filter(t -> t instanceof Sort).map(t -> (Sort)t).findFirst().orElseThrow(() -> new RuntimeException("No edit"));
        assertEquals(ImmutableList.of(new Pair<>(sortBy, Direction.ASCENDING)), sort.getSortBy());

        // Null means use row number
        @Nullable Column replaceKeyColumn = r.nextInt(5) == 1 ? null : sort.getData().getColumns().get(r.nextInt(numColumns));
        
        @SuppressWarnings("units")
        Supplier<@TableDataRowIndex Integer> makeRowIndex = () -> r.nextInt(length);
        @SuppressWarnings("units")
        Supplier<@TableDataColIndex Integer> makeColIndex = () -> r.nextInt(numColumns);

        HashMap<ColumnId, TreeMap<ComparableValue, ComparableEither<String, ComparableValue>>> replacementsSoFar = new HashMap<>();

        TableId sortId = sort.getId();
        
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
                replacementsSoFar.computeIfAbsent(sort.getData().getColumnIds().get(col), k -> new TreeMap<>())
                        .put(new ComparableValue(replaceKey), ComparableEither.right(new ComparableValue(value)));
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

        ManualEdit manualEdit = mainWindowActions._test_getTableManager().getAllTables().stream().filter(t -> t instanceof ManualEdit).map(t -> (ManualEdit)t).findFirst().orElseThrow(() -> new RuntimeException("No edit"));
        TableDisplay manualEditDisplay = TestUtil.checkNonNull((TableDisplay)TestUtil.<@Nullable TableDisplayBase>fx(() -> manualEdit.getDisplay()));
        
        int numFurtherEdits = 1 + r.nextInt(10);
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
                replacementsSoFar.computeIfAbsent(sort.getData().getColumnIds().get(col), k -> new TreeMap<>())
                        .put(new ComparableValue(replaceKey), ComparableEither.right(new ComparableValue(value)));
            }

            assertFalse("Alert should not be showing yet", lookup(".alert").tryQuery().isPresent());
            keyboardMoveTo(mainWindowActions._test_getVirtualGrid(), mainWindowActions._test_getTableManager(), manualEdit.getId(), row, col);
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
            // Change sort order:
            clickOn(".edit-sort-by");
            clickOn(".small-delete-button");
            clickOn(".fancy-list-add");
            sortBy = srcRS.getColumnIds().get(r.nextInt(numColumns));
            write(sortBy.getRaw());
            clickOn(".ok-button");
        }
        if (r.nextBoolean())
        {
            fail("TODO change original data");
        }
        
        // Now check output values by getting them from clipboard:
        TestUtil.sleep(500);
        showContextMenu(".table-display-table-title.transformation-table-title")
                .clickOn(".id-tableDisplay-menu-copyValues");
        TestUtil.sleep(1000);

        Optional<ImmutableList<LoadedColumnInfo>> clip = TestUtil.<Optional<ImmutableList<LoadedColumnInfo>>>fx(() -> ClipboardUtils.loadValuesFromClipboard(mainWindowActions._test_getTableManager().getTypeManager()));
        assertTrue(clip.isPresent());
        ImmutableList<LoadedColumnInfo> expected = makeExpected(sort.getData(), replaceKeyColumn == null ? null : replaceKeyColumn.getName(), replacementsSoFar, null);
        
        assertEquals(replacementsSoFar, manualEdit._test_getReplacements());
        checkEqual(expected, clip.get());
        checkEqual(expected, getGraphicalContent(mainWindowActions, manualEdit));
        
        fail("TODO add sort after manual edit and check that, too");
    }

    @OnThread(Tag.Simulation)
    private ImmutableList<LoadedColumnInfo> makeExpected(RecordSet original, @Nullable ColumnId replacementIdentifier, HashMap<ColumnId, TreeMap<ComparableValue, ComparableEither<String, ComparableValue>>> replacements, @Nullable ColumnId sortBy) throws UserException, InternalException
    {
        return Utility.mapListExI(original.getColumns(), column -> {
            TreeMap<ComparableValue, ComparableEither<String, ComparableValue>> colReplacements = replacements.getOrDefault(column.getName(), new TreeMap<>());
            ImmutableList.Builder<Either<String, @Value Object>> columnValues = ImmutableList.builderWithExpectedSize(original.getLength());
            for (int row = 0; row < original.getLength(); row++)
            {
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
                values.add(TestUtil.checkNonNull(TestUtil.<@Nullable Either<String, @Value Object>>fx(() -> {
                    VersionedSTF cell = mainWindowActions._test_getDataCell(tableDisplay._test_getDataPosition(rowFinal, colIndex));
                    if (cell == null)
                        return null;
                    return column.getType().getType().loadSingleItem(cell._test_getGraphicalText());
                })));
            }
            
            return new LoadedColumnInfo(column.getName(), column.getType().getType(), values.build());
        });
    }
}
