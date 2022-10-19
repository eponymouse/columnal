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

package test.gui;

import annotation.units.TableDataColIndex;
import annotation.units.TableDataRowIndex;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import xyz.columnal.log.Log;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.hamcrest.Matchers;
import org.junit.runner.RunWith;
import xyz.columnal.data.CellPosition;
import xyz.columnal.id.DataItemPosition;
import xyz.columnal.data.Table;
import xyz.columnal.gui.MainWindow.MainWindowActions;
import xyz.columnal.gui.table.app.TableDisplay;
import xyz.columnal.gui.grid.CellSelection;
import xyz.columnal.gui.grid.RectangleBounds;
import xyz.columnal.gui.grid.VirtualGrid;
import test.gen.GenImmediateData;
import test.gen.GenImmediateData.NumTables;
import test.gen.GenRandom;
import test.gui.trait.ScrollToTrait;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.Utility;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(JUnitQuickcheck.class)
public class TestKeyboardMovement extends FXApplicationTest implements ScrollToTrait
{
    /**
     * Check that keyboard moving around is consistent (right always selects more to the right, etc)
     * and reversible, and keeps the selected item in view.
     */
    @Property(trials=5)
    @OnThread(Tag.Simulation)
    public void testKeyboardMovement(@NumTables(minTables = 3, maxTables = 5) @From(GenImmediateData.class) GenImmediateData.ImmediateData_Mgr src, @From(GenRandom.class) Random r) throws Exception
    {
        MainWindowActions mainWindowActions = TAppUtil.openDataAsTable(windowToUse, src.mgr).get();
        TFXUtil.sleep(2000);
        
        VirtualGrid virtualGrid = mainWindowActions._test_getVirtualGrid();
        TFXUtil.fx_(windowToUse::requestFocus);
        assertTrue(TFXUtil.fx(() -> windowToUse.isFocused()));
        Log.debug("Focus owner: " + TFXUtil.fx(() -> windowToUse.getScene().getFocusOwner()));
        push(KeyCode.SHORTCUT, KeyCode.HOME);        
        assertEquals(Optional.of(true), TFXUtil.fx(() -> virtualGrid._test_getSelection().map(s -> s.getSelectionDisplayRectangle().contains(CellPosition.ORIGIN.offsetByRowCols(1, 1)))));
        checkSelectionOnScreen("Origin", virtualGrid);
        @SuppressWarnings("nullness")
        String tableSummary = TFXUtil.fx(() -> mainWindowActions._test_getTableManager().getAllTables().stream().map(t -> (TableDisplay)t.getDisplay()).map(t -> t.getPosition() + " - " + t.getBottomRightIncl()).collect(Collectors.joining()));
        
        // We go for a random walk right/down, then check that it is reversible:
        List<Pair<List<KeyCode>, RectangleBounds>> paths = new ArrayList<>();
        for (int i = 0; i < 12; i++)
        {
            int dist = 1 + r.nextInt(5);
            ArrayList<KeyCode> presses = new ArrayList<>(Utility.replicate(dist, r.nextInt(3) != 1 ? KeyCode.DOWN : KeyCode.RIGHT));
            RectangleBounds rectangleBounds = TFXUtil.fx(() -> virtualGrid._test_getSelection().map(s -> s.getSelectionDisplayRectangle())).get();
            for (ListIterator<KeyCode> iterator = presses.listIterator(); iterator.hasNext(); )
            {
                KeyCode keyCode = iterator.next();
                // Don't go RIGHT out of a table header as this can 
                // create an irreversible path:
                if (rectangleBounds.topLeftIncl.columnIndex != rectangleBounds.bottomRightIncl.columnIndex)
                {
                    keyCode = KeyCode.DOWN;
                    iterator.set(keyCode);
                }
                    
                Log.debug("Pressing: " + keyCode + " at rect: " + rectangleBounds); 
                        //" Focus owner: " + TFXUtil.fx(() -> targetWindow().getScene().getFocusOwner()));
                push(keyCode);
                // If we've reached edge, don't count keypress on the reverse trip:
                RectangleBounds newRectangleBounds = TFXUtil.fx(() -> virtualGrid._test_getSelection().map(s -> s.getSelectionDisplayRectangle())).get();
                if (newRectangleBounds.equals(rectangleBounds))
                {
                    iterator.remove();
                }
                rectangleBounds = newRectangleBounds;
            }
            if (paths.isEmpty() || !paths.get(paths.size() - 1).getSecond().equals(rectangleBounds))
            {
                paths.add(new Pair<>(
                    presses,
                    // Will throw if no selection, but that's fine as should be a test failure:
                    rectangleBounds
                ));
            }
            checkSelectionOnScreen(rectangleBounds.toString(), virtualGrid);
        }
        // TODO also check that trailing edge of selection is always moving strictly on
        for (int i = paths.size() - 1; i >= 0; i--)
        {
            assertEquals("Index " + i, Optional.of(paths.get(i).getSecond()), TFXUtil.fx(() -> virtualGrid._test_getSelection().map(s -> s.getSelectionDisplayRectangle())));
            List<KeyCode> first = paths.get(i).getFirst();
            for (int k = first.size() - 1; k >= 0; k--)
            {
                KeyCode keyCode = first.get(k);
                // Reverse the down/right into up/left:
                push(keyCode == KeyCode.DOWN ? KeyCode.UP : KeyCode.LEFT);
                RectangleBounds rectangleBounds = TFXUtil.fx(() -> virtualGrid._test_getSelection().map(s -> s.getSelectionDisplayRectangle())).get();
                Log.debug("Reversed " + keyCode + " to get to " + rectangleBounds);
            }
            checkSelectionOnScreen("Index " + i + " " + paths.get(i).getSecond().toString() + (i < paths.size() - 1 ? (" (from " + paths.get(i + 1).getSecond() + ")") : "") + " tables: " + tableSummary, virtualGrid);
        }
        // Should be back at origin:
        assertTrue(TFXUtil.fx(() -> virtualGrid._test_getSelection().map(s -> s.getSelectionDisplayRectangle().contains(CellPosition.ORIGIN.offsetByRowCols(1, 1))).orElse(false)));
    }

    @SuppressWarnings("nullness")
    @OnThread(Tag.Any)
    public void checkSelectionOnScreen(String prefix, VirtualGrid virtualGrid)
    {
        assertTrue(prefix, TFXUtil.fx(() -> windowToUse.isFocused()));
        Node selectionRect = TFXUtil.fx(() -> lookup(".virt-grid-selection-overlay").match(Node::isVisible).tryQuery().orElse(null));
        if (selectionRect == null)
        {
            // In case of a load:
            TFXUtil.sleep(5000);
            selectionRect = TFXUtil.fx(() -> lookup(".virt-grid-selection-overlay").match(Node::isVisible).tryQuery().orElse(null));
            Log.debug("Window: " + windowToUse + " Target: " + TFXUtil.fx(() -> targetWindow()) + " rect: " + TFXUtil.fx(() -> lookup(".virt-grid-selection-overlay").tryQuery().orElse(null)));
        }
        assertTrue(prefix, TFXUtil.fx(() -> windowToUse.isFocused()));
        assertNotNull(prefix, selectionRect);
        if (selectionRect != null)
        {
            @NonNull Node selectionRectFinal = selectionRect;
            Bounds selScreenBounds = TFXUtil.fx(() -> selectionRectFinal.localToScreen(selectionRectFinal.getBoundsInLocal()));
            Bounds gridScreenBounds = TFXUtil.fx(() -> virtualGrid.getNode().localToScreen(virtualGrid.getNode().getLayoutBounds()));
            // Selection must be at least part in view (ideally wholly in view, but if table is big, whole table
            // selection isn't all going to fit)
            //Log.debug("Grid bounds on screen: " + gridScreenBounds + " sel bounds " + selScreenBounds);
            // Incidental check that the grid node should not be massive:
            assertThat(prefix, gridScreenBounds.getWidth(), Matchers.lessThan(2000.0));
            assertThat(prefix, gridScreenBounds.getHeight(), Matchers.lessThan(2000.0));
            assertTrue(prefix, gridScreenBounds.intersects(selScreenBounds));
        }
    }

    @Property(trials=5)
    @OnThread(Tag.Simulation)
    public void testKeyboardScrollTo(@NumTables(minTables = 3, maxTables = 5) @From(GenImmediateData.class) GenImmediateData.ImmediateData_Mgr src, @From(GenRandom.class) Random r) throws Exception
    {
        MainWindowActions mainWindowActions = TAppUtil.openDataAsTable(windowToUse, src.mgr).get();
        TFXUtil.sleep(2000);
        assertThat(mainWindowActions._test_getSaveCount(), Matchers.lessThanOrEqualTo(1));

        VirtualGrid virtualGrid = mainWindowActions._test_getVirtualGrid();
        TFXUtil.fx_(windowToUse::requestFocus);
        assertTrue(TFXUtil.fx(() -> windowToUse.isFocused()));

        ImmutableList<Table> allTables = mainWindowActions._test_getTableManager().getAllTables();
        
        for (int i = 0; i < 6; i++)
        {
            Table t = allTables.get(r.nextInt(allTables.size()));
            @TableDataColIndex int col = DataItemPosition.col(r.nextInt(t.getData().getColumns().size()));
            @TableDataRowIndex int length = t.getData().getLength();
            if (length <= 0) // Can happen with empty table, just skip
                continue;
            @TableDataRowIndex int row = DataItemPosition.row(r.nextInt(length));
            
            keyboardMoveTo(virtualGrid, mainWindowActions._test_getTableManager(), t.getId(), row, col);

            Optional<CellSelection> selection = TFXUtil.fx(() -> virtualGrid._test_getSelection());
            @SuppressWarnings("nullness")
            CellPosition target = TFXUtil.fx(() -> t.getDisplay().getMostRecentPosition().offsetByRowCols(row + 3, col));
            assertTrue("Selected is " + selection.toString() + " aiming for " + target, TFXUtil.fx(() -> selection.map(s -> s.isExactly(target)).orElse(false)));
        }
    }
}
