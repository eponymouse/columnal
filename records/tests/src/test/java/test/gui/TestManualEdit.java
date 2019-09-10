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
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.runner.RunWith;
import records.data.*;
import records.data.Table.TableDisplayBase;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeUtility.ComparableValue;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.InvalidImmediateValueException;
import records.error.UserException;
import records.gui.DataCellSupplier.VersionedSTF;
import records.gui.MainWindow.MainWindowActions;
import records.gui.ManualEditEntriesDialog;
import records.gui.ManualEditEntriesDialog.Entry;
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
import test.gen.type.GenDataTypeMaker.MustHaveValues;
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
import utility.gui.FXUtility;
import utility.gui.FancyList;

import java.math.BigDecimal;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.Assert.*;

@RunWith(JUnitQuickcheck.class)
public class TestManualEdit extends FXApplicationTest implements ListUtilTrait, ScrollToTrait, PopupTrait, ClickTableLocationTrait, EnterStructuredValueTrait
{
    @SuppressWarnings("nullness") // TODO remove this laziness and fix
    @Property(trials = 3)
    @OnThread(Tag.Simulation)
    public void propManualEdit(
            @MustHaveValues @From(GenDataTypeMaker.class) GenDataTypeMaker.DataTypeMaker typeMaker,
            @From(GenRandom.class) Random r) throws Exception
    {
        int length = 1 + r.nextInt(50);
        int numColumns = 1 + r.nextInt(5);
        List<DataTypeAndValueMaker> columnTypes = Utility.replicateM_Ex(numColumns, () -> typeMaker.makeType());
        HashSet<ColumnId> usedColumnIds = new HashSet<>();
        List<SimulationFunction<RecordSet, EditableColumn>> makeColumns = Utility.<DataTypeAndValueMaker, SimulationFunction<RecordSet, EditableColumn>>mapListEx(columnTypes, columnType -> {
            ColumnId columnId;
            do
            {
                columnId = TestUtil.generateColumnId(new SourceOfRandomness(r));
            }
            while (usedColumnIds.contains(columnId));
            usedColumnIds.add(columnId);

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
        sleep(500);
        
        // The ID is fixed but the table is not, so we use ID 
        TableId sortId = mainWindowActions._test_getTableManager().getAllTables().stream().filter(t -> t instanceof Sort).findFirst().orElseThrow(() -> new RuntimeException("No edit")).getId();
        
        ExSupplier<Sort> findFirstSort = () -> (Sort)mainWindowActions._test_getTableManager().getSingleTableOrThrow(sortId);
        assertEquals(ImmutableList.of(new Pair<>(sortBy, Direction.ASCENDING)), findFirstSort.get().getSortBy());

        // Null means use row number
        int replaceKeyColumIndex = r.nextInt(numColumns);
        ExSupplier<@Nullable Column> findReplaceKeyColumn = r.nextInt(5) == 1 ? () -> null : () -> findFirstSort.get().getData().getColumns().get(replaceKeyColumIndex);
        
        // Start by checking the original sort is correct:
        TestUtil.sleep(500);
        TableDisplayBase firstSortDisplay = TestUtil.fx(() -> findFirstSort.get().getDisplay());
        keyboardMoveTo(mainWindowActions._test_getVirtualGrid(), firstSortDisplay.getMostRecentPosition());
        showContextMenu(withItemInBounds(lookup(".table-display-table-title.transformation-table-title"), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(firstSortDisplay.getMostRecentPosition(), firstSortDisplay.getMostRecentPosition()), (n, p) -> {}), null)
            .clickOn(".id-tableDisplay-menu-copyValues");
        TestUtil.sleep(1000);

        Optional<ImmutableList<LoadedColumnInfo>> editViaClip = TestUtil.<Optional<ImmutableList<LoadedColumnInfo>>>fx(() -> ClipboardUtils.loadValuesFromClipboard(mainWindowActions._test_getTableManager().getTypeManager()));
        assertTrue(editViaClip.isPresent());
        ImmutableList<LoadedColumnInfo> expected = makeExpected(srcRS, findReplaceKeyColumn.get() == null ? null : findReplaceKeyColumn.get().getName(), new HashMap<>(), sortBy);
        checkEqual(expected, editViaClip);
        checkEqual(expected, getGraphicalContent(mainWindowActions, findFirstSort.get()));

        

        ArrayList<Integer> rowIndexesWithUniqueKey = new ArrayList<>();
        // Each value is a list of row indexes
        TreeMap<ComparableValue, List<Integer>> freqCount = new TreeMap<>();
        if (findReplaceKeyColumn.get() != null)
        {
            for (int i = 0; i < findReplaceKeyColumn.get().getLength(); i++)
            {
                try
                {
                    freqCount.computeIfAbsent(new ComparableValue(findReplaceKeyColumn.get().getType().getCollapsed(i)), k -> new ArrayList<>()).add(i);
                }
                catch (InvalidImmediateValueException e)
                {
                    // Ignore this; not a key that can be matched anyway, so just don't add to map.
                }
            }
            freqCount.forEach((k, rowIndexes) -> {
                if (rowIndexes.size() == 1)
                    rowIndexesWithUniqueKey.add(rowIndexes.get(0));
            });
        }
        
        // Can happen e.g. with boolean keys, just going to have to give up:
        if (rowIndexesWithUniqueKey.isEmpty())
            return;
        
        // Pass true if you require a row with a unique replacement key 
        @SuppressWarnings("units")
        Function<Boolean, @TableDataRowIndex Integer> makeRowIndex = unique -> {
            if (unique)
                return rowIndexesWithUniqueKey.get(r.nextInt(rowIndexesWithUniqueKey.size()));
            else
                return r.nextInt(length);
        };
        @SuppressWarnings("units")
        Supplier<@TableDataColIndex Integer> makeColIndex = () -> r.nextInt(numColumns);

        HashMap<ColumnId, TreeMap<ComparableValue, ComparableEither<String, ComparableValue>>> replacementsSoFar = new HashMap<>();

        
        // There's two ways to create a new manual edit.  One is to just create it using the menu.  The other is to try to edit an existing transformation, and follow the popup which appears
        boolean madeManualEdit = false;
        if (r.nextBoolean())
        {
            @TableDataRowIndex int row = makeRowIndex.apply(true);
            @TableDataColIndex int col = makeColIndex.get();

            @Nullable @Value Object replaceKey;
            if (findReplaceKeyColumn.get() == null)
                replaceKey = DataTypeUtility.value(new BigDecimal(row));
            else
                replaceKey = TestUtil.getSingleCollapsedData(findReplaceKeyColumn.get().getType(), row).leftToNull();

            @Value Object value = columnTypes.get(col).makeValue();
            // Only works if value is different:
            if (TestUtil.getSingleCollapsedData(findFirstSort.get().getData().getColumns().get(col).getType(), row).eitherEx(e -> -1, x -> Utility.compareValues(x, value)) != 0)
            {
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

                CellPosition cellPos = keyboardMoveTo(mainWindowActions._test_getVirtualGrid(), mainWindowActions._test_getTableManager(), sortId, row, col);

                VersionedSTF oldCell = TestUtil.fx(() -> mainWindowActions._test_getDataCell(cellPos));
                String oldContent = TestUtil.fx(() -> oldCell._test_getGraphicalText());
                //if (!oldContent.contains("\u2026"))
                    //assertEquals(oldContent, TestUtil.getSingleCollapsedData(findFirstSort.get().getData().getColumns().get(col).getType(), row));
                                
                push(KeyCode.ENTER);

                enterStructuredValue(columnTypes.get(col).getDataType(), value, r, true, false);
                push(KeyCode.ENTER);

                assertTrue("Alert should be showing asking whether to create manual edit", lookup(".alert").tryQuery().isPresent());
                clickOn(".yes-button");
                sleep(500);
                assertFalse("Alert should be dismissed", lookup(".alert").tryQuery().isPresent());

                sleep(1000);
                VersionedSTF newCell = TestUtil.fx(() -> mainWindowActions._test_getDataCell(cellPos));
                //if (!oldContent.contains("\u2026"))
                    //assertEquals(oldContent, TestUtil.getSingleCollapsedData(findFirstSort.get().getData().getColumns().get(col).getType(), row));
                assertEquals("Cell: " + newCell, oldContent, TestUtil.fx(() -> newCell._test_getGraphicalText()));
                
                // Now fall through to fill in same details as creating directly...
                madeManualEdit = true;
            }
        }
        
        if (!madeManualEdit)
        {
            // Let's create it directly.
            CellPosition targetPos = TestUtil.fx(() -> mainWindowActions._test_getTableManager().getNextInsertPosition(null));


            keyboardMoveTo(mainWindowActions._test_getVirtualGrid(), targetPos);
            clickOnItemInBounds(from(TestUtil.fx(() -> mainWindowActions._test_getVirtualGrid().getNode())), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(targetPos, targetPos), MouseButton.PRIMARY);
            TestUtil.delay(100);
            clickOn(".id-new-transform");
            TestUtil.delay(100);
            scrollTo(".id-transform-edit");
            clickOn(".id-transform-edit");
            TestUtil.delay(100);
            write(sortId.getRaw());
            push(KeyCode.ENTER);
            TestUtil.delay(1000);
        }
        
        if (findReplaceKeyColumn.get() == null)
            clickOn(".id-manual-edit-byrownum");
        else
        {
            clickOn(".id-manual-edit-bycolumn");
            push(KeyCode.TAB);
            write(findReplaceKeyColumn.get().getName().getRaw());
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
            @TableDataRowIndex int row = makeRowIndex.apply(true);
            @TableDataColIndex int col = makeColIndex.get();
            
            @Nullable @Value Object replaceKey;
            if (findReplaceKeyColumn.get() == null)
                replaceKey = DataTypeUtility.value(new BigDecimal(row));
            else
                replaceKey = TestUtil.getSingleCollapsedData(findReplaceKeyColumn.get().getType(), row).leftToNull();
            
            @Value Object value = columnTypes.get(col).makeValue();
            ComparableEither<String, ComparableValue> toEnter = ComparableEither.right(new ComparableValue(value));
            if (TestUtil.getSingleCollapsedData(findFirstSort.get().getData().getColumns().get(col).getType(), row).eitherEx(e -> -1, x -> Utility.compareValues(x, value)) == 0)
            {
                // Make error instead:
                toEnter = ComparableEither.left("@" + r.nextInt());
            }
            if (replaceKey != null)
            {
                replacementsSoFar.computeIfAbsent(findFirstSort.get().getData().getColumnIds().get(col), k -> new TreeMap<>())
                        .put(new ComparableValue(replaceKey), toEnter);
            }

            assertFalse("Alert should not be showing yet", lookup(".alert").tryQuery().isPresent());
            keyboardMoveTo(mainWindowActions._test_getVirtualGrid(), mainWindowActions._test_getTableManager(), manualEditId, row, col);
            sleep(500);

            assertFalse("Alert should not be showing yet", lookup(".alert").tryQuery().isPresent());
            push(KeyCode.ENTER);
            // If key has an error, we should get a dialog.
            if (replaceKey == null)
            {
                sleep(1500);
                assertTrue("Alert should be showing about invalid key for column " + (findReplaceKeyColumn.get() == null ? " <row>" : findReplaceKeyColumn.get().getName().getRaw()), lookup(".alert").tryQuery().isPresent());
                clickOn(".ok-button");
                assertFalse("Alert still showing after OK", lookup(".alert").tryQuery().isPresent());
            }
            else
            {
                assertFalse("False alert showing, but valid key", lookup(".alert").tryQuery().isPresent());
                toEnter.eitherEx_(s -> {
                    push(TestUtil.ctrlCmd(), KeyCode.A);
                    push(KeyCode.DELETE);
                    push(KeyCode.HOME);
                    write(s);
                }, v -> enterStructuredValue(columnTypes.get(col).getDataType(), v.getValue(), r, true, false));
                push(KeyCode.ENTER);
                //TestUtil.fx_(() -> dumpScreenshot());
            }
        }
        
        if (r.nextInt(3) == 1)
        {
            CellPosition target = TestUtil.fx(() -> findFirstSort.get().getDisplay().getMostRecentPosition().offsetByRowCols(0, -1));
            keyboardMoveTo(mainWindowActions._test_getVirtualGrid(), target);
            Point2D selScreenPos = TestUtil.fx(() -> FXUtility.getCentre(mainWindowActions._test_getVirtualGrid().getRectangleBoundsScreen(new RectangleBounds(target, target))));
            // Change sort order of source:
            // We might see both sort edit links on screen, so pick the closest one:
            clickOn(lookup(".edit-sort-by").queryAll().stream().min(Comparator.comparing((Node n) -> {
                Bounds bounds = TestUtil.fx(() -> n.localToScreen(n.getBoundsInLocal()));
                return Math.hypot(FXUtility.getCentre(bounds).getX() - selScreenPos.getX(), FXUtility.getCentre(bounds).getY() - selScreenPos.getY());
            })).get());
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
                @TableDataRowIndex int row = makeRowIndex.apply(false);
                @TableDataColIndex int col = makeColIndex.get();
                @Value Object value = columnTypes.get(col).makeValue();
                if (findReplaceKeyColumn.get() != null && findManualEdit.get().getData().getColumns().get(col).getName().equals(findReplaceKeyColumn.get().getName()))
                {
                    // Don't introduce duplicate keys:
                    if (freqCount.containsKey(new ComparableValue(value)))
                        continue;
                }
                keyboardMoveTo(mainWindowActions._test_getVirtualGrid(), mainWindowActions._test_getTableManager(), findFirstSort.get().getSrcTableId(), row, col);
                sleep(500);
                assertFalse("Alert should not be showing", lookup(".alert").tryQuery().isPresent());
                push(KeyCode.ENTER);
                enterStructuredValue(columnTypes.get(col).getDataType(), value, r, true, false);
                push(KeyCode.ENTER);
            }
            sleep(1000);

            // Check original sort again:
            //TableDisplayBase firstSortDisplay = TestUtil.fx(() -> findFirstSort.get().getDisplay());
            keyboardMoveTo(mainWindowActions._test_getVirtualGrid(), firstSortDisplay.getMostRecentPosition());
            showContextMenu(withItemInBounds(lookup(".table-display-table-title.transformation-table-title"), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(firstSortDisplay.getMostRecentPosition(), firstSortDisplay.getMostRecentPosition()), (n, p) -> {}), null)
                .clickOn(".id-tableDisplay-menu-copyValues");
            TestUtil.sleep(1000);

            editViaClip = TestUtil.<Optional<ImmutableList<LoadedColumnInfo>>>fx(() -> ClipboardUtils.loadValuesFromClipboard(mainWindowActions._test_getTableManager().getTypeManager()));
            assertTrue(editViaClip.isPresent());
            expected = makeExpected(mainWindowActions._test_getTableManager().getAllTables().stream().filter(t -> t instanceof ImmediateDataSource).findFirst().orElseThrow(() -> new RuntimeException()).getData(), findReplaceKeyColumn.get() == null ? null : findReplaceKeyColumn.get().getName(), new HashMap<>(), sortBy);
            checkEqual(expected, editViaClip);
            checkEqual(expected, getGraphicalContent(mainWindowActions, findFirstSort.get()));
        }
        
        // Now check output values by getting them from clipboard:
        TestUtil.sleep(500);
        keyboardMoveTo(mainWindowActions._test_getVirtualGrid(), manualEditDisplay.getMostRecentPosition());
        showContextMenu(withItemInBounds(lookup(".table-display-table-title.transformation-table-title"), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(manualEditDisplay.getMostRecentPosition(), manualEditDisplay.getMostRecentPosition()), (n, p) -> {}), null)
                .clickOn(".id-tableDisplay-menu-copyValues");
        TestUtil.sleep(1000);

        editViaClip = TestUtil.<Optional<ImmutableList<LoadedColumnInfo>>>fx(() -> ClipboardUtils.loadValuesFromClipboard(mainWindowActions._test_getTableManager().getTypeManager()));
        assertTrue(editViaClip.isPresent());
        expected = makeExpected(findFirstSort.get().getData(), findReplaceKeyColumn.get() == null ? null : findReplaceKeyColumn.get().getName(), replacementsSoFar, sortBy);
        
        assertEquals(replacementsSoFar, findManualEdit.get()._test_getReplacements());
        checkEqual(expected, editViaClip);
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
        ImmutableList<LoadedColumnInfo> expectedSecondSort = makeExpected(findManualEdit.get().getData(), findReplaceKeyColumn.get() == null ? null : findReplaceKeyColumn.get().getName(), new HashMap<>(), secondSortBy);

        checkEqual(expectedSecondSort, secondSortViaClip);
        checkEqual(expectedSecondSort, getGraphicalContent(mainWindowActions, secondSort));
        
        keyboardMoveTo(mainWindowActions._test_getVirtualGrid(), mainWindowActions._test_getTableManager(), manualEditId, DataItemPosition.row(0), DataItemPosition.col(1));
        // Note -- this has been flaky in the past because when the text link is wrapped, the bounds are unreliable for clicking;
        // a click on the centre can click where the link is not present.  Bottom left seems like best bet since if it does wrap,
        // it will occupy the start of the last line...
        clickOn(point(".manual-edit-entries").atPosition(Pos.BOTTOM_LEFT).query().add(2, -2));
        sleep(500);
        FancyList<ManualEditEntriesDialog.Entry, ?> listEntries = TestUtil.checkNonNull(lookup(".fancy-list").<FancyList<ManualEditEntriesDialog.Entry, ?>.FancyListScrollPane>tryQuery().orElse(null))._test_getList();
        
        Supplier<List<ManualEditEntriesDialog.Entry>> getEntries = () -> replacementsSoFar.entrySet().stream().flatMap(e -> e.getValue().entrySet().stream().map(e2 -> new ManualEditEntriesDialog.Entry(e2.getKey(), e.getKey(), e2.getValue()))).collect(ImmutableList.toImmutableList());
        
        // Pick a few to delete:
        for (int i = 0; i < 3 && !replacementsSoFar.isEmpty(); i++)
        {
            List<ManualEditEntriesDialog.Entry> possibleItems = getEntries.get();
            
            ManualEditEntriesDialog.Entry toDelete = possibleItems.get(r.nextInt(possibleItems.size()));

            @NonNull Node cell = TestUtil.checkNonNull(TestUtil.fx(() -> listEntries._test_scrollToItem(toDelete)));
            clickOn(TestUtil.fx(() -> cell.lookup(".small-delete")));

            TreeMap<ComparableValue, ComparableEither<String, ComparableValue>> map = TestUtil.checkNonNull(replacementsSoFar.get(toDelete.getReplacementColumn()));
            map.remove(toDelete.getIdentifierValue());
            if (map.isEmpty())
                replacementsSoFar.remove(toDelete.getReplacementColumn());
        }
        
        if (r.nextBoolean() && !replacementsSoFar.isEmpty())
        {
            // Try using the hyperlink functionality to close the dialog, on random chance:
            List<Entry> entries = getEntries.get();
            Entry toClick = entries.get(r.nextInt(entries.size()));
            @NonNull Node cell = TestUtil.checkNonNull(TestUtil.fx(() -> listEntries._test_scrollToItem(toClick)));
            clickOn(TestUtil.fx(() -> cell.lookup(".jump-to-link")));
            sleep(700);
            assertNull(lookup(".fancy-list").tryQuery().orElse(null));
            @TableDataColIndex int col = DataItemPosition.col(Utility.findFirstIndex(findManualEdit.get().getData().getColumnIds(), c -> c.equals(toClick.getReplacementColumn())).orElseThrow(() -> new RuntimeException("Could not find replacement column: " + toClick.getReplacementColumn())));
            int row = -1;
            if (findReplaceKeyColumn.get() == null)
                row = ((Number)toClick.getIdentifierValue().getValue()).intValue();
            else
            {
                DataTypeValue keyColType = findReplaceKeyColumn.get().getType();

                int len = findManualEdit.get().getData().getLength();
                for (int i = 0; i < len; i++)
                {
                    try
                    {
                        if (Utility.compareValues(keyColType.getCollapsed(i), toClick.getIdentifierValue().getValue()) == 0)
                        {
                            row = i;
                            break;
                        }
                    }
                    catch (InvalidImmediateValueException e)
                    {
                        // Can't be this one; keep going
                    }
                }
            }
            assertNotEquals(-1, row);
            @TableDataRowIndex int rowFinal = DataItemPosition.row(row);
            assertEquals("Clicking on " + toClick + " key: " + (findReplaceKeyColumn.get() == null ? "<row>" : findReplaceKeyColumn.get().getName()) + " row: " + row + " col: " + col, TestUtil.fx(() -> manualEditDisplay.getDataPosition(rowFinal, col)), TestUtil.<@Nullable CellPosition>fx(() -> mainWindowActions._test_getVirtualGrid()._test_getSelection().map(s -> s.getActivateTarget()).orElse(null)));
            assertNull(lookup(".id-create-table").match(Node::isVisible).tryQuery().orElse(null));
        }
        else
        {
            clickOn(".close-button");
        }
        sleep(1000);

        // Check everything is up to date after deletions:
        assertEquals(replacementsSoFar, findManualEdit.get()._test_getReplacements());

        keyboardMoveTo(mainWindowActions._test_getVirtualGrid(), manualEditDisplay.getMostRecentPosition());
        showContextMenu(withItemInBounds(lookup(".table-display-table-title.transformation-table-title"), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(manualEditDisplay.getMostRecentPosition(), manualEditDisplay.getMostRecentPosition()), (n, p) -> {}), null)
                .clickOn(".id-tableDisplay-menu-copyValues");
        
        editViaClip = TestUtil.<Optional<ImmutableList<LoadedColumnInfo>>>fx(() -> ClipboardUtils.loadValuesFromClipboard(mainWindowActions._test_getTableManager().getTypeManager()));
        assertTrue(editViaClip.isPresent());
        expected = makeExpected(findFirstSort.get().getData(), findReplaceKeyColumn.get() == null ? null : findReplaceKeyColumn.get().getName(), replacementsSoFar, sortBy);
        checkEqual(expected, editViaClip);
        checkEqual(expected, getGraphicalContent(mainWindowActions, findManualEdit.get()));
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

        // We effectively pair each integer with the value from that row, then sort by the values and discard them.
        // This leaves a sorted list of indexes into the original table.
        if (sortBy != null)
        {
            DataTypeValue sortByColumn = original.getColumn(sortBy).getType();
            List<ComparableEither<String, ComparableValue>> sortByData = Utility.mapList(TestUtil.getAllCollapsedData(sortByColumn, original.getLength()), x -> ComparableEither.fromEither(x.map(ComparableValue::new)));
            Collections.sort(sortMap, Comparator.<Integer, ComparableEither<String, ComparableValue>>comparing(i -> sortByData.get(i)));
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
    private void checkEqual(ImmutableList<LoadedColumnInfo> expected, Optional<ImmutableList<LoadedColumnInfo>> actual) throws InternalException, UserException
    {
        if (!actual.isPresent())
            return; // Missing data, so can't compare
        assertEquals(expected.size(), actual.get().size());
        for (int colIndex = 0; colIndex < expected.size(); colIndex++)
        {
            LoadedColumnInfo expCol = expected.get(colIndex);
            LoadedColumnInfo actCol = actual.get().get(colIndex);
            
            assertEquals(expCol.columnName, actCol.columnName);
            assertEquals(expCol.dataType, actCol.dataType);
            TestUtil.assertValueListEitherEqual("Column " + expCol.columnName + " (" + colIndex + ")", expCol.dataValues, actCol.dataValues);
        }
    }

    @OnThread(Tag.Simulation)
    @SuppressWarnings("units")
    private Optional<ImmutableList<LoadedColumnInfo>> getGraphicalContent(MainWindowActions mainWindowActions, Table table) throws InternalException, UserException
    {
        RecordSet tableData = table.getData();
        // Horribly hacky:
        @OnThread(Tag.Any)
        class MissingCellException extends RuntimeException {}
        
        try
        {
            return Optional.of(Utility.mapListExI_Index(tableData.getColumns(), (colIndex, column) -> {
                TableDisplay tableDisplay = TestUtil.checkNonNull(TestUtil.<@Nullable TableDisplay>fx(() -> (TableDisplay) table.getDisplay()));
                ImmutableList.Builder<Either<String, @Value Object>> values = ImmutableList.builder();
                for (int row = 0; row < tableData.getLength(); row++)
                {
                    int rowFinal = row;
                    Either<String, @Value Object> internalContent = TestUtil.getSingleCollapsedData(column.getType(), rowFinal);
                    @Nullable Either<String, @Value Object> cellValue = TestUtil.<@Nullable Either<String, @Value Object>>fx(() -> {
                        CellPosition dataPosition = tableDisplay.getDataPosition(rowFinal, colIndex);
                        VersionedSTF cell = mainWindowActions._test_getDataCell(dataPosition);
                        if (cell == null)
                        {
                            System.err.println("No cell found for " + rowFinal + ", " + colIndex + " which resolves to " + dataPosition);
                            return null;
                        }
                        String content = cell._test_getGraphicalText();
                        // Bit of a hack, but need to deal with truncated numbers, so we get direct content if truncated:
                        if (DataTypeUtility.isNumber(column.getType().getType()) && content.contains("\u2026"))
                            return internalContent;
                        return column.getType().getType().loadSingleItem(content.trim());
                    });
                    if (cellValue == null)
                        throw new MissingCellException();
                    values.add(cellValue);
                }

                return new LoadedColumnInfo(column.getName(), column.getType().getType(), values.build());
            }));
        }
        catch (MissingCellException e)
        {
            // If any are missing, we can't sensibly proceed:
            return Optional.empty();
        }
    }
}
