package test.gui;

import annotation.units.TableDataColIndex;
import annotation.units.TableDataRowIndex;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import log.Log;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.testfx.api.FxRobotInterface;
import org.testfx.framework.junit.ApplicationTest;
import records.data.CellPosition;
import records.data.DataItemPosition;
import records.data.Table;
import records.data.Table.TableDisplayBase;
import records.gui.MainWindow.MainWindowActions;
import records.gui.TableDisplay;
import records.gui.grid.RectangleBounds;
import records.gui.grid.VirtualGrid;
import test.TestUtil;
import test.gen.GenImmediateData;
import test.gen.GenImmediateData.NumTables;
import test.gen.GenRandom;
import test.gui.trait.ScrollToTrait;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

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
        MainWindowActions mainWindowActions = TestUtil.openDataAsTable(windowToUse, src.mgr).get();
        TestUtil.sleep(2000);
        
        VirtualGrid virtualGrid = mainWindowActions._test_getVirtualGrid();
        TestUtil.fx_(windowToUse::requestFocus);
        assertTrue(TestUtil.fx(() -> windowToUse.isFocused()));
        Log.debug("Focus owner: " + TestUtil.fx(() -> windowToUse.getScene().getFocusOwner()));
        push(KeyCode.CONTROL, KeyCode.HOME);        
        assertEquals(Optional.of(true), TestUtil.fx(() -> virtualGrid._test_getSelection().map(s -> s.getSelectionDisplayRectangle().contains(CellPosition.ORIGIN))));
        checkSelectionOnScreen("Origin", virtualGrid);
        @SuppressWarnings("nullness")
        String tableSummary = TestUtil.fx(() -> mainWindowActions._test_getTableManager().getAllTables().stream().map(t -> (TableDisplay)t.getDisplay()).map(t -> t.getPosition() + " - " + t.getBottomRightIncl()).collect(Collectors.joining()));
        
        // We go for a random walk right/down, then check that it is reversible:
        List<Pair<List<KeyCode>, RectangleBounds>> paths = new ArrayList<>();
        for (int i = 0; i < 12; i++)
        {
            int dist = 1 + r.nextInt(5);
            ArrayList<KeyCode> presses = new ArrayList<>(Utility.replicate(dist, r.nextInt(3) != 1 ? KeyCode.DOWN : KeyCode.RIGHT));
            RectangleBounds rectangleBounds = TestUtil.fx(() -> virtualGrid._test_getSelection().map(s -> s.getSelectionDisplayRectangle())).get();
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
                        //" Focus owner: " + TestUtil.fx(() -> targetWindow().getScene().getFocusOwner()));
                push(keyCode);
                // If we've reached edge, don't count keypress on the reverse trip:
                RectangleBounds newRectangleBounds = TestUtil.fx(() -> virtualGrid._test_getSelection().map(s -> s.getSelectionDisplayRectangle())).get();
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
            assertEquals("Index " + i, Optional.of(paths.get(i).getSecond()), TestUtil.fx(() -> virtualGrid._test_getSelection().map(s -> s.getSelectionDisplayRectangle())));
            List<KeyCode> first = paths.get(i).getFirst();
            for (int k = first.size() - 1; k >= 0; k--)
            {
                KeyCode keyCode = first.get(k);
                // Reverse the down/right into up/left:
                push(keyCode == KeyCode.DOWN ? KeyCode.UP : KeyCode.LEFT);
                RectangleBounds rectangleBounds = TestUtil.fx(() -> virtualGrid._test_getSelection().map(s -> s.getSelectionDisplayRectangle())).get();
                Log.debug("Reversed " + keyCode + " to get to " + rectangleBounds);
            }
            checkSelectionOnScreen("Index " + i + " " + paths.get(i).getSecond().toString() + (i < paths.size() - 1 ? (" (from " + paths.get(i + 1).getSecond() + ")") : "") + " tables: " + tableSummary, virtualGrid);
        }
        // Should be back at origin:
        assertTrue(TestUtil.fx(() -> virtualGrid._test_getSelection().map(s -> s.getSelectionDisplayRectangle().contains(CellPosition.ORIGIN)).orElse(false)));
    }

    @SuppressWarnings("nullness")
    @OnThread(Tag.Any)
    public void checkSelectionOnScreen(String prefix, VirtualGrid virtualGrid)
    {
        assertTrue(prefix, TestUtil.fx(() -> windowToUse.isFocused()));
        Node selectionRect = lookup(".virt-grid-selection-overlay").match(Node::isVisible).tryQuery().orElse(null);
        if (selectionRect == null)
        {
            // In case of a load:
            TestUtil.delay(5000);
            selectionRect = lookup(".virt-grid-selection-overlay").match(Node::isVisible).tryQuery().orElse(null);
            Log.debug("Window: " + windowToUse + " Target: " + targetWindow() + " rect: " + lookup(".virt-grid-selection-overlay").tryQuery().orElse(null));
        }
        assertTrue(prefix, TestUtil.fx(() -> windowToUse.isFocused()));
        assertNotNull(prefix, selectionRect);
        if (selectionRect != null)
        {
            @NonNull Node selectionRectFinal = selectionRect;
            Bounds selScreenBounds = TestUtil.fx(() -> selectionRectFinal.localToScreen(selectionRectFinal.getBoundsInLocal()));
            Bounds gridScreenBounds = TestUtil.fx(() -> virtualGrid.getNode().localToScreen(virtualGrid.getNode().getLayoutBounds()));
            // Selection must be at least part in view (ideally wholly in view, but if table is big, whole table
            // selection isn't all going to fit)
            //Log.debug("Grid bounds on screen: " + gridScreenBounds + " sel bounds " + selScreenBounds);
            // Incidental check that the grid node should not be massive:
            MatcherAssert.assertThat(prefix, gridScreenBounds.getWidth(), Matchers.lessThan(2000.0));
            MatcherAssert.assertThat(prefix, gridScreenBounds.getHeight(), Matchers.lessThan(2000.0));
            assertTrue(prefix, gridScreenBounds.intersects(selScreenBounds));
        }
    }

    @Property(trials=5)
    @OnThread(Tag.Simulation)
    public void testKeyboardScrollTo(@NumTables(minTables = 3, maxTables = 5) @From(GenImmediateData.class) GenImmediateData.ImmediateData_Mgr src, @From(GenRandom.class) Random r) throws Exception
    {
        MainWindowActions mainWindowActions = TestUtil.openDataAsTable(windowToUse, src.mgr).get();
        TestUtil.sleep(2000);

        VirtualGrid virtualGrid = mainWindowActions._test_getVirtualGrid();
        TestUtil.fx_(windowToUse::requestFocus);
        assertTrue(TestUtil.fx(() -> windowToUse.isFocused()));

        ImmutableList<Table> allTables = mainWindowActions._test_getTableManager().getAllTables();
        
        for (int i = 0; i < 6; i++)
        {
            Table t = allTables.get(r.nextInt(allTables.size()));
            @TableDataColIndex int col = DataItemPosition.col(r.nextInt(t.getData().getColumns().size()));
            @TableDataRowIndex int row = DataItemPosition.row(r.nextInt(t.getData().getLength()));
            if (row < 0 || col < 0) // Can happen with empty table, just skip
                continue;
            
            keyboardMoveTo(virtualGrid, mainWindowActions._test_getTableManager(), t.getId(), row, col);
        }
    }
}
