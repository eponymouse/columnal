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

import annotation.qual.Value;
import annotation.units.TableDataColIndex;
import annotation.units.TableDataRowIndex;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.internal.GeometricDistribution;
import com.pholser.junit.quickcheck.internal.generator.SimpleGenerationStatus;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.text.Text;
import org.testfx.util.WaitForAsyncUtils;
import test.gui.TAppUtil;
import test.gui.TFXUtil;
import xyz.columnal.id.ColumnId;
import xyz.columnal.id.DataItemPosition;
import xyz.columnal.id.TableId;
import xyz.columnal.log.Log;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.runner.RunWith;
import xyz.columnal.data.*;
import xyz.columnal.data.Table.TableDisplayBase;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.DataTypeUtility.ComparableValue;
import xyz.columnal.data.datatype.DataTypeValue;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.InvalidImmediateValueException;
import xyz.columnal.error.UserException;
import xyz.columnal.gui.table.DataCellSupplier.VersionedSTF;
import xyz.columnal.gui.MainWindow.MainWindowActions;
import xyz.columnal.gui.ManualEditEntriesDialog;
import xyz.columnal.gui.ManualEditEntriesDialog.Entry;
import xyz.columnal.gui.grid.RectangleBounds;
import xyz.columnal.gui.table.app.TableDisplay;
import xyz.columnal.importers.ClipboardUtils;
import xyz.columnal.importers.ClipboardUtils.LoadedColumnInfo;
import xyz.columnal.transformations.ManualEdit;
import xyz.columnal.transformations.Sort;
import test.gen.GenDataAndTransforms;
import test.gen.GenRandom;
import test.gen.GenValueSpecifiedType;
import test.gui.trait.ClickTableLocationTrait;
import test.gui.trait.EnterStructuredValueTrait;
import test.gui.trait.ListUtilTrait;
import test.gui.trait.PopupTrait;
import test.gui.trait.ScrollToTrait;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.ComparableEither;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.ExSupplier;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.function.simulation.SimulationFunction;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.gui.Clickable;
import xyz.columnal.utility.gui.FXUtility;
import xyz.columnal.utility.gui.FancyList;

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
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.Assert.*;

@RunWith(JUnitQuickcheck.class)
public class TestManualEdit extends FXApplicationTest implements ListUtilTrait, ScrollToTrait, PopupTrait, ClickTableLocationTrait, EnterStructuredValueTrait
{
    private static final class ValueMaker
    {
        private final DataType dataType;
        private static final GenValueSpecifiedType genValue = new GenValueSpecifiedType();
        private static @MonotonicNonNull SourceOfRandomness sourceOfRandomness;
        private static @MonotonicNonNull GenerationStatus generationStatus;
        
        public ValueMaker(DataType dataType, Random random)
        {
            this.dataType = dataType;
            if (sourceOfRandomness == null)
            {
                sourceOfRandomness = new SourceOfRandomness(random);
                generationStatus = new SimpleGenerationStatus(new GeometricDistribution(), sourceOfRandomness, 10);
            }
        }

        public DataType getDataType()
        {
            return dataType;
        }

        @OnThread(Tag.Simulation)
        public @Value Object makeValue() throws InternalException, UserException
        {
            // Can't ever be null because initialised when first object is created
            return genValue.generate(TBasicUtil.checkNonNull(sourceOfRandomness), TBasicUtil.checkNonNull(generationStatus)).makeValue(dataType);
        }
    }
    
    @Property(trials = 5)
    @OnThread(Tag.Simulation)
    public void propManualEdit(
            @From(GenDataAndTransforms.class) TableManager original,
            @From(GenRandom.class) Random r) throws Exception
    {
        MainWindowActions mainWindowActions = TAppUtil.openDataAsTable(windowToUse, original).get();
        TFXUtil.sleep(5000);

        // Pick a src table for the manual edit: 
        ImmutableList<Table> allSrcs = mainWindowActions._test_getTableManager().getAllTables().stream().filter(t -> t instanceof Transformation).collect(ImmutableList.toImmutableList());
        TableId srcTableId = allSrcs.get(r.nextInt(allSrcs.size())).getId();
        // The table may get re-run when things change, but its ID will not, so we fetch it each time by its ID:
        ExSupplier<Transformation> findSrc = () -> (Transformation) mainWindowActions._test_getTableManager().getSingleTableOrThrow(srcTableId);
        // The source table's types will never get modified:
        ImmutableList<ValueMaker> columnTypes = calculateColumnTypes(r, findSrc);

        // -1 means use row number
        int srcColumnCount = findSrc.get().getData().getColumns().size();
        MatcherAssert.assertThat("Table" + findSrc.get() + " has one or more columns", srcColumnCount, Matchers.greaterThanOrEqualTo(1));
        int replaceKeyColumnIndex = r.nextInt(5) == 1 ? -1 : r.nextInt(srcColumnCount);
        // Fetches the column that is being used for the manual edit primary key.  Null means using row number.
        ExSupplier<@Nullable Column> findReplaceKeyColumn = () -> replaceKeyColumnIndex == -1 ? null : findSrc.get().getData().getColumns().get(replaceKeyColumnIndex);

        // Start by checking the original values are as expected if we copy them::
        TFXUtil.sleep(500);
        @SuppressWarnings("nullness")
        @NonNull TableDisplayBase firstSortDisplay = TFXUtil.fx(() -> findSrc.get().getDisplay());
        keyboardMoveTo(mainWindowActions._test_getVirtualGrid(), firstSortDisplay.getMostRecentPosition());
        showContextMenu(withItemInBounds(".table-display-table-title.transformation-table-title", mainWindowActions._test_getVirtualGrid(), new RectangleBounds(firstSortDisplay.getMostRecentPosition(), firstSortDisplay.getMostRecentPosition()), (n, p) -> {
        }), null)
            .clickOn(".id-tableDisplay-menu-copyValues");
        TFXUtil.sleep(1000);

        Optional<ImmutableList<LoadedColumnInfo>> editViaClip = TFXUtil.<Optional<ImmutableList<LoadedColumnInfo>>>fx(() -> ClipboardUtils.loadValuesFromClipboard(mainWindowActions._test_getTableManager().getTypeManager()));
        assertTrue(editViaClip.isPresent());
        ImmutableList<LoadedColumnInfo> expected = makeExpected(findSrc.get().getData(), null, new HashMap<>(), null);
        checkEqual(expected, editViaClip);
        checkEqual(expected, getGraphicalContent(mainWindowActions, findSrc.get()));

        // Each item is a row number of a row that does not share its
        // key with any other row:
        ArrayList<Integer> rowIndexesWithUniqueKey = new ArrayList<>();
        // Each value is a list of row indexes
        TreeMap<ComparableValue, List<Integer>> freqCount = new TreeMap<>();
        {
            Column replaceKeyColumn = findReplaceKeyColumn.get();
            if (replaceKeyColumn != null)
            {
                for (int i = 0; i < replaceKeyColumn.getLength(); i++)
                {
                    try
                    {
                        freqCount.computeIfAbsent(new ComparableValue(replaceKeyColumn.getType().getCollapsed(i)), k -> new ArrayList<>()).add(i);
                    } catch (InvalidImmediateValueException e)
                    {
                        // Ignore this; not a key that can be matched anyway, so just don't add to map.
                    }
                }
                freqCount.forEach((k, rowIndexes) -> {
                    if (rowIndexes.size() == 1)
                        rowIndexesWithUniqueKey.add(rowIndexes.get(0));
                });
            }
        }
        
        // Can happen e.g. with boolean keys, just going to have to give up:
        if (rowIndexesWithUniqueKey.isEmpty())
        {
            Log.debug("No indexes with unique key");
            return;
        }

        // Now time to make some replacements

        @TableDataRowIndex int length = findSrc.get().getData().getLength();
        // Pass true if you require a row with a unique replacement key,
        // pass false if you just want a valid row number
        @SuppressWarnings("units")
        Function<Boolean, @TableDataRowIndex Integer> makeRowIndex = unique -> {
            if (unique)
                return rowIndexesWithUniqueKey.get(r.nextInt(rowIndexesWithUniqueKey.size()));
            else
                return r.nextInt(length);
        };
        int numColumns = findSrc.get().getData().getColumns().size();
        @SuppressWarnings("units")
        Supplier<@TableDataColIndex Integer> makeColIndex = () -> r.nextInt(numColumns);

        HashMap<ColumnId, TreeMap<ComparableValue, ComparableEither<String, ComparableValue>>> replacementsSoFar = new HashMap<>();

        
        // There's two ways to create a new manual edit transformation.  One is to just create it using the menu.  The other is to try to edit an existing transformation, and follow the popup which appears
        boolean madeManualEdit = false;
        if (r.nextBoolean())
        {
            System.out.println("Attempting to creating manual edit transformation by editing source: " + findSrc.get().getClass());
            // Let's pick a location and edit it
            @TableDataRowIndex int row = makeRowIndex.apply(true);
            @TableDataColIndex int col = makeColIndex.get();

            @Nullable @Value Object replaceKey;
            Column replaceKeyColumn = findReplaceKeyColumn.get();
            if (replaceKeyColumn == null)
                replaceKey = DataTypeUtility.value(new BigDecimal(row));
            else
                replaceKey = TBasicUtil.getSingleCollapsedData(replaceKeyColumn.getType(), row).leftToNull();

            SimulationFunction<@Value Object, Boolean> equalToExistingKey = v -> TBasicUtil.getSingleCollapsedData(findSrc.get().getData().getColumns().get(col).getType(), row).eitherEx(e -> -1, x -> Utility.compareValues(x, v)) == 0;
            @Value Object value = columnTypes.get(col).makeValue();
            for (int i = 0; i < 5 && equalToExistingKey.apply(value); i++)
            {
                value = columnTypes.get(col).makeValue();
            }
            
            // Only works if value is different:
            if (!equalToExistingKey.apply(value))
            {
                if (replaceKey != null)
                {
                    replacementsSoFar.computeIfAbsent(findSrc.get().getData().getColumnIds().get(col), k -> new TreeMap<>())
                            .put(new ComparableValue(replaceKey), ComparableEither.right(new ComparableValue(value)));
                }
                else
                {
                    // Trying to edit row with error key, so
                    // not clear what should happen.  Just leave it...
                    return;
                }

                CellPosition cellPos = keyboardMoveTo(mainWindowActions._test_getVirtualGrid(), mainWindowActions._test_getTableManager(), srcTableId, row, col);

                VersionedSTF oldCell = TBasicUtil.checkNonNull(TFXUtil.<@Nullable VersionedSTF>fx(() -> mainWindowActions._test_getDataCell(cellPos)));
                String oldContent = TFXUtil.fx(() -> oldCell._test_getGraphicalText());
                //if (!oldContent.contains("\u2026"))
                    //assertEquals(oldContent, TestUtil.getSingleCollapsedData(findSrc.get().getData().getColumns().get(col).getType(), row));
                                
                push(KeyCode.ENTER);

                enterStructuredValue(columnTypes.get(col).getDataType(), value, r, true, false);
                push(KeyCode.ENTER);

                assertShowing("Alert should be showing asking whether to create manual edit after editing " + findSrc.get().getClass() + " column " + findSrc.get().getData().getColumnIds().get(col).getRaw(), ".alert");
                clickOn(".yes-button");
                sleep(500);
                assertNotShowing("Alert should be dismissed", ".alert");

                sleep(1000);
                VersionedSTF newCell = TBasicUtil.checkNonNull(TFXUtil.<@Nullable VersionedSTF>fx(() -> mainWindowActions._test_getDataCell(cellPos)));
                //if (!oldContent.contains("\u2026"))
                    //assertEquals(oldContent, TestUtil.getSingleCollapsedData(findSrc.get().getData().getColumns().get(col).getType(), row));
                assertEquals("Cell: " + newCell, oldContent, TFXUtil.fx(() -> newCell._test_getGraphicalText()));
                
                // Now fall through to fill in same details as creating directly...
                madeManualEdit = true;
            }
        }
        
        if (!madeManualEdit)
        {
            System.out.println("Creating manual edit transformation directly");
            // Let's create it directly.
            CellPosition targetPos = TFXUtil.fx(() -> mainWindowActions._test_getTableManager().getNextInsertPosition(null));


            keyboardMoveTo(mainWindowActions._test_getVirtualGrid(), targetPos);
            clickOnItemInBounds(fromNode(TFXUtil.fx(() -> mainWindowActions._test_getVirtualGrid().getNode())), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(targetPos, targetPos), MouseButton.PRIMARY);
            TFXUtil.sleep(100);
            clickOn(".id-new-transform");
            TFXUtil.sleep(100);
            scrollTo(".id-transform-edit");
            clickOn(".id-transform-edit");
            TFXUtil.sleep(100);
            write(srcTableId.getRaw());
            push(KeyCode.ENTER);
            TFXUtil.sleep(1000);
        }

        {
            Column replaceKeyColumn = findReplaceKeyColumn.get();
            if (replaceKeyColumn == null)
                clickOn(".id-manual-edit-byrownum");
            else
            {
                clickOn(".id-manual-edit-bycolumn");
                push(KeyCode.TAB);
                write(replaceKeyColumn.getName().getRaw());
            }
        }
        clickOn(".ok-button");
        sleep(1000);

        // Now we should have a transformation, time to do some edits

        TableId manualEditId = ManualEdit.suggestedName(Utility.onNullable(findReplaceKeyColumn.get(), c -> c.getName()), replacementsSoFar);
        ExSupplier<ManualEdit> findManualEdit = () -> (ManualEdit) mainWindowActions._test_getTableManager().getSingleTableOrThrow(manualEditId);
        TableDisplay manualEditDisplay = TBasicUtil.checkNonNull((TableDisplay) TFXUtil.<@Nullable TableDisplayBase>fx(() -> findManualEdit.get().getDisplay()));

        ImmutableSet<TableId> idsBeforeNewSort = getTableIdSet(mainWindowActions);
        // Add a sort transformation, sorting the edit transformation by a random column:
        CellPosition targetSortPos = TFXUtil.fx(() -> mainWindowActions._test_getTableManager().getNextInsertPosition(manualEditId).offsetByRowCols(2, 0));
        keyboardMoveTo(mainWindowActions._test_getVirtualGrid(), targetSortPos);
        System.out.println("Focused: " + getFocusOwner() + " aiming for " + targetSortPos);
        clickOnItemInBounds(fromNode(TFXUtil.fx(() -> mainWindowActions._test_getVirtualGrid().getNode())), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(targetSortPos, targetSortPos), MouseButton.PRIMARY);
        TFXUtil.sleep(1000);
        clickOn(".id-new-transform");
        TFXUtil.sleep(1000);
        clickOn(".id-transform-sort");
        TFXUtil.sleep(1000);
        write(manualEditId.getRaw());
        push(KeyCode.ENTER);
        ColumnId sortEditByColumn = findSrc.get().getData().getColumnIds().get(r.nextInt(numColumns));
        write(sortEditByColumn.getRaw());
        moveAndDismissPopupsAtPos(point(".ok-button"));
        clickOn();
        // Hack to get id of new sort:
        TableId sortId = Sort.suggestedName(ImmutableList.of(new Pair<>(sortEditByColumn, "")));        
        
        // Now make the further manual edits:
        int numFurtherEdits = r.nextInt(10);
        for (int i = 0; i < numFurtherEdits; i++)
        {
            @TableDataRowIndex int row = makeRowIndex.apply(true);
            @TableDataColIndex int col = makeColIndex.get();
            
            @Nullable @Value Object replaceKey;
            {
                Column replaceKeyColumn = findReplaceKeyColumn.get();
                if (replaceKeyColumn == null)
                    replaceKey = DataTypeUtility.value(new BigDecimal(row));
                else
                    replaceKey = TBasicUtil.getSingleCollapsedData(replaceKeyColumn.getType(), row).leftToNull();
            }
            
            @Value Object value = columnTypes.get(col).makeValue();
            ComparableEither<String, ComparableValue> toEnter = ComparableEither.right(new ComparableValue(value));
            if (TBasicUtil.getSingleCollapsedData(findSrc.get().getData().getColumns().get(col).getType(), row).eitherEx(e -> -1, x -> Utility.compareValues(x, value)) == 0)
            {
                // Make error instead:
                toEnter = ComparableEither.left("@" + r.nextInt());
            }
            if (replaceKey != null)
            {
                replacementsSoFar.computeIfAbsent(findSrc.get().getData().getColumnIds().get(col), k -> new TreeMap<>())
                        .put(new ComparableValue(replaceKey), toEnter);
            }

            assertNotShowing("Alert should not be showing yet", ".alert");
            keyboardMoveTo(mainWindowActions._test_getVirtualGrid(), mainWindowActions._test_getTableManager(), manualEditId, row, col);
            sleep(500);

            assertNotShowing("Alert should not be showing yet", ".alert");
            push(KeyCode.ENTER);
            // If key has an error, we should get a dialog.
            if (replaceKey == null)
            {
                sleep(1500);
                Column replaceKeyColumn = findReplaceKeyColumn.get();
                assertShowing("Alert should be showing about invalid key for column " + (replaceKeyColumn == null ? " <row>" : replaceKeyColumn.getName().getRaw()), ".alert");
                clickOn(".ok-button");
                assertNotShowing("Alert still showing after OK", ".alert");
            }
            else
            {
                assertNotShowing("False alert showing, but valid key", ".alert");
                toEnter.eitherEx_(s -> {
                    push(TFXUtil.ctrlCmd(), KeyCode.A);
                    push(KeyCode.DELETE);
                    push(KeyCode.HOME);
                    write(s);
                }, v -> enterStructuredValue(columnTypes.get(col).getDataType(), v.getValue(), r, true, false));
                push(KeyCode.ENTER);
                //TFXUtil.fx_(() -> dumpScreenshot());
            }
        }
        
        // On random chance, change sort order of the transformation that is sorting the manual edit:
        if (r.nextInt(3) == 1)
        {
            TableId sortIdFinal = sortId;
            CellPosition target = TFXUtil.fx(() -> {
                @SuppressWarnings("nullness")
                @NonNull TableDisplayBase display = mainWindowActions._test_getTableManager().getSingleTableOrThrow(sortIdFinal).getDisplay();
                return display.getMostRecentPosition().offsetByRowCols(0, findSrc.get().getData().getColumns().size() - 1);
            });
            TFXUtil.fx_(() -> mainWindowActions._test_getVirtualGrid()._test_ensureVisible(target));
            Point2D selScreenPos = TFXUtil.fx(() -> FXUtility.getCentre(mainWindowActions._test_getVirtualGrid().getRectangleBoundsScreen(new RectangleBounds(target, target))));
            // Change sort order of source:
            // We might see both sort edit links on screen, so pick the closest one:
            // Sometimes the table hats overlap, so click programmatically:
            Node editLink = TFXUtil.fx(() -> lookup(".edit-sort-by").queryAll().stream().min(Comparator.comparing((Node n) -> {
                Bounds bounds = n.localToScreen(n.getBoundsInLocal());
                return Math.hypot(FXUtility.getCentre(bounds).getX() - selScreenPos.getX(), FXUtility.getCentre(bounds).getY() - selScreenPos.getY());
            })).get());
            clickLinkSafely(editLink);
            sleep(200);
            clickOn(".small-delete");
            sleep(200);
            clickOn(".id-fancylist-add");
            sortEditByColumn = findSrc.get().getData().getColumnIds().get(r.nextInt(numColumns));
            write(sortEditByColumn.getRaw());
            sortId = Sort.suggestedName(ImmutableList.of(new Pair<>(sortEditByColumn, "")));
            clickOn(".ok-button");
            sleep(1000);
        }
        
        // On random chance, change the data being edited:
        /*
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
                keyboardMoveTo(mainWindowActions._test_getVirtualGrid(), mainWindowActions._test_getTableManager(), findSrc.get().getSources().iterator().next(), row, col);
                sleep(500);
                assertFalse("Alert should not be showing", lookup(".alert").tryQuery().isPresent());
                push(KeyCode.ENTER);
                enterStructuredValue(columnTypes.get(col).getDataType(), value, r, true, false);
                push(KeyCode.ENTER);
            }
            sleep(1000);

            // Check original sort again:
            //TableDisplayBase firstSortDisplay = TFXUtil.fx(() -> findSrc.get().getDisplay());
            keyboardMoveTo(mainWindowActions._test_getVirtualGrid(), firstSortDisplay.getMostRecentPosition());
            showContextMenu(withItemInBounds(lookup(".table-display-table-title.transformation-table-title"), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(firstSortDisplay.getMostRecentPosition(), firstSortDisplay.getMostRecentPosition()), (n, p) -> {}), null)
                .clickOn(".id-tableDisplay-menu-copyValues");
            TestUtil.sleep(1000);

            editViaClip = TestUtil.<Optional<ImmutableList<LoadedColumnInfo>>>fx(() -> ClipboardUtils.loadValuesFromClipboard(mainWindowActions._test_getTableManager().getTypeManager()));
            assertTrue(editViaClip.isPresent());
            expected = makeExpected(mainWindowActions._test_getTableManager().getAllTables().stream().filter(t -> t instanceof ImmediateDataSource).findFirst().orElseThrow(() -> new RuntimeException()).getData(), findReplaceKeyColumn.get() == null ? null : findReplaceKeyColumn.get().getName(), new HashMap<>(), sortBy);
            checkEqual(expected, editViaClip);
            checkEqual(expected, getGraphicalContent(mainWindowActions, findSrc.get()));
        }
        */
        
        // Now check output values by getting them from clipboard:
        TFXUtil.sleep(500);
        keyboardMoveTo(mainWindowActions._test_getVirtualGrid(), manualEditDisplay.getMostRecentPosition());
        showContextMenu(withItemInBounds(".table-display-table-title.transformation-table-title", mainWindowActions._test_getVirtualGrid(), new RectangleBounds(manualEditDisplay.getMostRecentPosition(), manualEditDisplay.getMostRecentPosition()), (n, p) -> {}), null)
                .clickOn(".id-tableDisplay-menu-copyValues");
        TFXUtil.sleep(1000);

        editViaClip = TFXUtil.<Optional<ImmutableList<LoadedColumnInfo>>>fx(() -> ClipboardUtils.loadValuesFromClipboard(mainWindowActions._test_getTableManager().getTypeManager()));
        assertTrue(editViaClip.isPresent());
        {
            Column replaceKeyColumn = findReplaceKeyColumn.get();
            expected = makeExpected(findSrc.get().getData(), replaceKeyColumn == null ? null : replaceKeyColumn.getName(), replacementsSoFar, null);
        }
        
        assertEquals(replacementsSoFar, findManualEdit.get()._test_getReplacements());
        checkEqual(expected, editViaClip);
        checkEqual(expected, getGraphicalContent(mainWindowActions, findManualEdit.get()));
        
        // Copy the values from the resulting sort:
        TableId secondSortId = sortId;
        Sort secondSort = mainWindowActions._test_getTableManager().getAllTables().stream().filter(t -> t instanceof Sort && t.getId().equals(secondSortId)).map(t -> (Sort)t).findFirst().orElseThrow(() -> new RuntimeException("No edit"));
        TableDisplay secondSortDisplay = (TableDisplay) TBasicUtil.checkNonNull(TFXUtil.<@Nullable TableDisplayBase>fx(() -> secondSort.getDisplay()));
        keyboardMoveTo(mainWindowActions._test_getVirtualGrid(), secondSortDisplay.getMostRecentPosition());
        showContextMenu(withItemInBounds(".table-display-table-title.transformation-table-title", mainWindowActions._test_getVirtualGrid(), new RectangleBounds(secondSortDisplay.getMostRecentPosition(), secondSortDisplay.getMostRecentPosition()), (n, p) -> {}), null)
                .clickOn(".id-tableDisplay-menu-copyValues");
        TFXUtil.sleep(1000);

        Optional<ImmutableList<LoadedColumnInfo>> secondSortViaClip = TFXUtil.<Optional<ImmutableList<LoadedColumnInfo>>>fx(() -> ClipboardUtils.loadValuesFromClipboard(mainWindowActions._test_getTableManager().getTypeManager()));
        assertTrue(secondSortViaClip.isPresent());
        Column replaceKeyColumn = findReplaceKeyColumn.get();
        ImmutableList<LoadedColumnInfo> expectedSecondSort = makeExpected(findManualEdit.get().getData(), replaceKeyColumn == null ? null : replaceKeyColumn.getName(), new HashMap<>(), sortEditByColumn);

        checkEqual(expectedSecondSort, secondSortViaClip);
        checkEqual(expectedSecondSort, getGraphicalContent(mainWindowActions, secondSort));
        
        keyboardMoveTo(mainWindowActions._test_getVirtualGrid(), mainWindowActions._test_getTableManager(), manualEditId, DataItemPosition.row(0), DataItemPosition.col(findManualEdit.get().getData().getColumns().size() - 1));
        clickLinkSafely(waitForOne(".manual-edit-entries"));
        sleep(500);
        FancyList<ManualEditEntriesDialog.Entry, ?> listEntries = this.<FancyList<ManualEditEntriesDialog.Entry, ?>.FancyListScrollPane>waitForOne(".fancy-list")._test_getList();
        
        Supplier<List<ManualEditEntriesDialog.Entry>> getEntries = () -> replacementsSoFar.entrySet().stream().flatMap(e -> e.getValue().entrySet().stream().map(e2 -> new ManualEditEntriesDialog.Entry(e2.getKey(), e.getKey(), e2.getValue()))).collect(ImmutableList.toImmutableList());
        
        // Pick a few to delete:
        for (int i = 0; i < 3 && !replacementsSoFar.isEmpty(); i++)
        {
            List<ManualEditEntriesDialog.Entry> possibleItems = getEntries.get();
            
            ManualEditEntriesDialog.Entry toDelete = possibleItems.get(r.nextInt(possibleItems.size()));

            @NonNull Node cell = TBasicUtil.checkNonNull(TFXUtil.fx(() -> listEntries._test_scrollToItem(toDelete)));
            clickOn(TFXUtil.fx(() -> cell.lookup(".small-delete")));

            TreeMap<ComparableValue, ComparableEither<String, ComparableValue>> map = TBasicUtil.checkNonNull(replacementsSoFar.get(toDelete.getReplacementColumn()));
            map.remove(toDelete.getIdentifierValue());
            if (map.isEmpty())
                replacementsSoFar.remove(toDelete.getReplacementColumn());
        }
        
        if (r.nextBoolean() && !replacementsSoFar.isEmpty())
        {
            // Try using the hyperlink functionality to close the dialog, on random chance:
            List<Entry> entries = getEntries.get();
            Entry toClick = entries.get(r.nextInt(entries.size()));
            @NonNull Node cell = TBasicUtil.checkNonNull(TFXUtil.fx(() -> listEntries._test_scrollToItem(toClick)));
            clickOn(TFXUtil.fx(() -> cell.lookup(".jump-to-link")));
            assertNotShowing("List", ".fancy-list");
            @TableDataColIndex int col = DataItemPosition.col(Utility.findFirstIndex(findManualEdit.get().getData().getColumnIds(), c -> c.equals(toClick.getReplacementColumn())).orElseThrow(() -> new RuntimeException("Could not find replacement column: " + toClick.getReplacementColumn())));
            int row = -1;
            replaceKeyColumn = findReplaceKeyColumn.get();
            if (replaceKeyColumn == null)
                row = ((Number)toClick.getIdentifierValue().getValue()).intValue();
            else
            {
                DataTypeValue keyColType = replaceKeyColumn.getType();

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
            replaceKeyColumn = findReplaceKeyColumn.get();
            Column replaceKeyColumnFinal = replaceKeyColumn;
            waitForSuccess(() -> assertEquals("Clicking on " + toClick + " key: " + (replaceKeyColumnFinal == null ? "<row>" : replaceKeyColumnFinal.getName()) + " row: " + rowFinal + " col: " + col, TFXUtil.fx(() -> manualEditDisplay.getDataPosition(rowFinal, col)), TFXUtil.<@Nullable CellPosition>fx(() -> mainWindowActions._test_getVirtualGrid()._test_getSelection().map(s -> s.getActivateTarget()).orElse(null))));
            assertNull(TFXUtil.fx(() -> lookup(".id-create-table").match(Node::isVisible).tryQuery().orElse(null)));
        }
        else
        {
            clickOn(".close-button");
        }
        sleep(1000);

        // Check everything is up to date after deletions:
        assertEquals(replacementsSoFar, findManualEdit.get()._test_getReplacements());

        keyboardMoveTo(mainWindowActions._test_getVirtualGrid(), manualEditDisplay.getMostRecentPosition());
        showContextMenu(withItemInBounds(".table-display-table-title.transformation-table-title", mainWindowActions._test_getVirtualGrid(), new RectangleBounds(manualEditDisplay.getMostRecentPosition(), manualEditDisplay.getMostRecentPosition()), (n, p) -> {}), null)
                .clickOn(".id-tableDisplay-menu-copyValues");
        
        editViaClip = TFXUtil.<Optional<ImmutableList<LoadedColumnInfo>>>fx(() -> ClipboardUtils.loadValuesFromClipboard(mainWindowActions._test_getTableManager().getTypeManager()));
        assertTrue(editViaClip.isPresent());
        replaceKeyColumn = findReplaceKeyColumn.get();
        expected = makeExpected(findSrc.get().getData(), replaceKeyColumn == null ? null : replaceKeyColumn.getName(), replacementsSoFar, null);
        checkEqual(expected, editViaClip);
        checkEqual(expected, getGraphicalContent(mainWindowActions, findManualEdit.get()));
    }

    private static void clickLinkSafely(Node editLink) throws InternalException
    {
        Text t = Utility.cast(editLink, Text.class);
        // Need to use asyncFx because it may show a modal dialog; we don't want to wait:
        WaitForAsyncUtils.asyncFx(() -> {
            if (t.getUserData() instanceof Clickable)
            {
                Bounds screenBounds = editLink.localToScreen(editLink.getBoundsInLocal());
                ((Clickable)t.getUserData())._test_onClick(MouseButton.PRIMARY, new Point2D(screenBounds.getCenterX(), screenBounds.getCenterY()));
            }
        });
    }

    public ImmutableList<ValueMaker> calculateColumnTypes(@When(seed = 2L) @From(GenRandom.class) Random r, ExSupplier<Transformation> findSrc) throws UserException, InternalException
    {
        ImmutableList.Builder<ValueMaker> columnTypesBuilder = ImmutableList.builder();
        for (Column column : findSrc.get().getData().getColumns())
        {
            DataType type = column.getType().getType();
            columnTypesBuilder.add(new ValueMaker(type, r));
        }
        return columnTypesBuilder.build();
    }

    public ImmutableSet<TableId> getTableIdSet(MainWindowActions mainWindowActions)
    {
        return mainWindowActions._test_getTableManager().getAllTables().stream().map(t -> t.getId()).collect(ImmutableSet.toImmutableSet());
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
            List<ComparableEither<String, ComparableValue>> sortByData = Utility.mapList(TBasicUtil.getAllCollapsedData(sortByColumn, original.getLength()), x -> ComparableEither.fromEither(x.map(ComparableValue::new)));
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
                Either<String, @Value Object> rowKey = replacementIdentifier == null ? Either.<String, @Value Object>right(DataTypeUtility.value(row)) : TBasicUtil.getSingleCollapsedData(original.getColumn(replacementIdentifier).getType(), row);
                @Nullable ComparableEither<String, ComparableValue> replacement = rowKey.<@Nullable ComparableEither<String, ComparableValue>>either(err -> null, k -> colReplacements.get(new ComparableValue(k)));
                if (replacement != null)
                    columnValues.add(replacement.<@Value Object>map(r -> r.getValue()));
                else
                    columnValues.add(TBasicUtil.getSingleCollapsedData(column.getType(), row));
                    
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
            TBasicUtil.assertValueListEitherEqual("Column " + expCol.columnName + " (" + colIndex + ")", expCol.dataValues, actCol.dataValues);
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
                TableDisplay tableDisplay = TBasicUtil.checkNonNull(TFXUtil.<@Nullable TableDisplay>fx(() -> (TableDisplay) table.getDisplay()));
                ImmutableList.Builder<Either<String, @Value Object>> values = ImmutableList.builder();
                for (int row = 0; row < tableData.getLength(); row++)
                {
                    int rowFinal = row;
                    Either<String, @Value Object> internalContent = TBasicUtil.getSingleCollapsedData(column.getType(), rowFinal);
                    @Nullable Either<String, @Value Object> cellValue = TFXUtil.<@Nullable Either<String, @Value Object>>fx(() -> {
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
