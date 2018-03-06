package test.gui;

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
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.runner.RunWith;
import org.testfx.api.FxRobotInterface;
import org.testfx.framework.junit.ApplicationTest;
import records.data.CellPosition;
import records.gui.grid.RectangleBounds;
import records.gui.grid.VirtualGrid;
import test.TestUtil;
import test.gen.GenImmediateData;
import test.gen.GenImmediateData.NumTables;
import test.gen.GenRandom;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(JUnitQuickcheck.class)
public class TestKeyboardMovement extends ApplicationTest
{
    @OnThread(Tag.Any)
    @SuppressWarnings("nullness")
    private Stage windowToUse;

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public void start(Stage stage) throws Exception
    {
        windowToUse = stage;
    }

    /**
     * Check that keyboard moving around is consistent (right always selects more to the right, etc)
     * and reversible, and keeps the selected item in view.
     */
    @Property(trials=5)
    @OnThread(Tag.Simulation)
    public void testKeyboardMovement(@When(seed=1L) @NumTables(minTables = 3, maxTables = 5) @From(GenImmediateData.class) GenImmediateData.ImmediateData_Mgr src, @When(seed=1L) @From(GenRandom.class) Random r) throws Exception
    {
        VirtualGrid virtualGrid = TestUtil.openDataAsTable(windowToUse, src.mgr).get().getSecond();
        targetWindow(windowToUse);
        push(KeyCode.CONTROL, KeyCode.HOME);
        assertTrue(TestUtil.fx(() -> virtualGrid._test_getSelection().map(s -> s.getSelectionDisplayRectangle().contains(CellPosition.ORIGIN)).orElse(false)));
        
        // We go for a random walk right/down, then check that it is reversible:
        List<Pair<List<KeyCode>, RectangleBounds>> paths = new ArrayList<>();
        for (int i = 0; i < 20; i++)
        {
            int dist = 1 + r.nextInt(5);
            List<KeyCode> presses = Utility.replicate(dist, r.nextBoolean() ? KeyCode.DOWN : KeyCode.RIGHT);
            for (KeyCode keyCode : presses)
            {
                //Log.debug("Pressing: " + keyCode + " Focus owner: " + TestUtil.fx(() -> targetWindow().getScene().getFocusOwner()));
                push(keyCode);
            }
            paths.add(new Pair<>(
                presses,
                // Will throw if no selection, but that's fine as should be a test failure:
                TestUtil.fx(() -> virtualGrid._test_getSelection().map(s -> s.getSelectionDisplayRectangle())).get()
            ));
            checkSelectionOnScreen(virtualGrid);
        }
        // TODO also check that trailing edge of selection is always moving strictly on
        for (int i = paths.size() - 1; i >= 0; i--)
        {
            assertEquals("Index " + i, Optional.of(paths.get(i).getSecond()), TestUtil.fx(() -> virtualGrid._test_getSelection().map(s -> s.getSelectionDisplayRectangle())));
            for (KeyCode keyCode : paths.get(i).getFirst())
            {
                // Reverse the down/right into up/left:
                push(keyCode == KeyCode.DOWN ? KeyCode.UP : KeyCode.LEFT);
            }
            checkSelectionOnScreen(virtualGrid);
        }
        // Should be back at origin:
        assertTrue(TestUtil.fx(() -> virtualGrid._test_getSelection().map(s -> s.getSelectionDisplayRectangle().contains(CellPosition.ORIGIN)).orElse(false)));
    }

    @OnThread(Tag.Any)
    public void checkSelectionOnScreen(VirtualGrid virtualGrid)
    {
        Node selectionRect = lookup(".virt-grid-selection-overlay").match(Node::isVisible).query();
        assertNotNull(selectionRect);
        if (selectionRect != null)
        {
            @NonNull Node selectionRectFinal = selectionRect;
            Bounds selScreenBounds = TestUtil.fx(() -> selectionRectFinal.localToScreen(selectionRectFinal.getBoundsInLocal()));
            Bounds gridScreenBounds = TestUtil.fx(() -> virtualGrid.getNode().localToScreen(virtualGrid.getNode().getLayoutBounds()));
            // Selection must be at least part in view (ideally wholly in view, but if table is big, whole table
            // selection isn't all going to fit)
            Log.debug("Grid bounds on screen: " + gridScreenBounds);
            // Incidental check that the grid node should not be massive:
            MatcherAssert.assertThat(gridScreenBounds.getWidth(), Matchers.lessThan(2000.0));
            MatcherAssert.assertThat(gridScreenBounds.getHeight(), Matchers.lessThan(2000.0));
            assertTrue(gridScreenBounds.intersects(selScreenBounds));
        }
    }
}
