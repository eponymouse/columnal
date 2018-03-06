package test.gui;

import javafx.geometry.Bounds;
import javafx.geometry.HorizontalDirection;
import javafx.geometry.VerticalDirection;
import javafx.scene.Node;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.testfx.api.FxRobotInterface;
import org.testfx.service.query.NodeQuery;
import records.data.CellPosition;
import records.gui.grid.VirtualGrid;
import test.TestUtil;
import threadchecker.OnThread;
import threadchecker.Tag;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public interface ScrollToTrait extends FxRobotInterface
{
    // Scrolls until the entire node is on screen
    @OnThread(Tag.Any)
    default public void scrollTo(String nodeLocator)
    {
        scrollTo(lookup(nodeLocator));
    }

    // Scrolls until the entire node is on screen
    @OnThread(Tag.Any)
    default public void scrollTo(NodeQuery nodeLocator)
    {
        @Nullable Node targetQ = nodeLocator.query();
        if (targetQ == null)
        {
            System.err.println("No such node to scroll to: " + nodeLocator);
            return;
        }
        @NonNull Node target = targetQ;

        // Find enclosing scroll:
        ScrollPane enclosingScroll = TestUtil.fx(() -> {
            Node n = target.getParent();
            while (n != null && !(n instanceof ScrollPane))
            {
                n = n.getParent();
            }
            return (ScrollPane) n;
        });
        if (enclosingScroll == null)
        {
            fail("Could not find enclosing scroll pane");
        }

        Bounds viewBounds = TestUtil.fx(() -> enclosingScroll.localToScreen(enclosingScroll.getViewportBounds()));
        // We limit how many times we will scroll, to avoid an infinite loop in case of test failure:
        for (int i = 0;i < 100 && TestUtil.fx(() -> target.localToScreen(target.getBoundsInLocal()).getMaxX()) > viewBounds.getMaxX(); i++)
        {
            System.out.println("Scrolling RIGHT");
            clickOn(".main-scroll > .scroll-bar:horizontal > .increment-button");
            //scroll(HorizontalDirection.RIGHT);
        }
        for (int i = 0; i < 100 && TestUtil.fx(() -> target.localToScreen(target.getBoundsInLocal()).getMinX()) < viewBounds.getMinX(); i++)
        {
            System.out.println("Scrolling LEFT");
            clickOn(".main-scroll > .scroll-bar:horizontal > .decrement-button");
            //scroll(HorizontalDirection.LEFT);
        }
        for (int i = 0; i < 100 && TestUtil.fx(() -> target.localToScreen(target.getBoundsInLocal()).getMaxY()) > viewBounds.getMaxY(); i++)
        {
            System.out.println("Scrolling DOWN");
            //scroll(VerticalDirection.DOWN);
            clickOn(".main-scroll > .scroll-bar:vertical > .increment-button");
        }
        for (int i = 0; i < 100 && TestUtil.fx(() -> target.localToScreen(target.getBoundsInLocal()).getMinY()) < viewBounds.getMinY(); i++)
        {
            System.out.println("Scrolling UP");
            //scroll(VerticalDirection.UP);
            clickOn(".main-scroll > .scroll-bar:vertical > .decrement-button");
        }
        assertTrue(viewBounds.contains(TestUtil.fx(() -> target.localToScreen(target.getBoundsInLocal()))));
    }
    
    @OnThread(Tag.Any)
    default void keyboardMoveTo(VirtualGrid virtualGrid, CellPosition target)
    {
        push(KeyCode.CONTROL, KeyCode.HOME);
        // First go to correct row:
        for (int i = 0; i < target.rowIndex; i++)
        {
            push(KeyCode.DOWN);
        }
        
        while (TestUtil.fx(() -> virtualGrid._test_getSelection().map(s -> s.positionToEnsureInView().columnIndex).orElse(Integer.MAX_VALUE)) < target.columnIndex)
        {
            push(KeyCode.RIGHT);
        }
    }
}
