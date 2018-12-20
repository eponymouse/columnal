package test.gui.trait;

import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.geometry.HorizontalDirection;
import javafx.geometry.VerticalDirection;
import javafx.scene.Node;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import log.Log;
import org.apache.commons.lang3.SystemUtils;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.testfx.api.FxRobotInterface;
import org.testfx.service.finder.NodeFinder;
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
        scrollTo(targetQ);
    }

    // Scrolls until the entire node is on screen
    @OnThread(Tag.Any)
    default public void scrollTo(Node target)
    {
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

        Bounds viewBounds = TestUtil.fx(() -> enclosingScroll.getContent().localToScreen(enclosingScroll.getBoundsInLocal()));
        // Move, ready for potentially scrolling:
        moveTo(viewBounds.getMinX() + 2, viewBounds.getMinY() + 2);
        // We limit how many times we will scroll, to avoid an infinite loop in case of test failure:
        for (int i = 0;i < 100 && TestUtil.fx(() -> target.localToScreen(target.getBoundsInLocal()).getMaxX()) > viewBounds.getMaxX(); i++)
        {
            System.out.println("Scrolling RIGHT");
            clickOrScroll(
                from(enclosingScroll)
                .lookup(".scroll-bar")
                .match((ScrollBar sb) -> TestUtil.fx(() -> sb.getPseudoClassStates().contains(PseudoClass.getPseudoClass("horizontal"))))
                .lookup(".increment-button"), () -> scroll(HorizontalDirection.RIGHT));
            //scroll(HorizontalDirection.RIGHT);
        }
        for (int i = 0; i < 100 && TestUtil.fx(() -> target.localToScreen(target.getBoundsInLocal()).getMinX()) < viewBounds.getMinX(); i++)
        {
            System.out.println("Scrolling LEFT");
            clickOrScroll(from(enclosingScroll)
                    .lookup(".scroll-bar")
                    .match((ScrollBar sb) -> TestUtil.fx(() -> sb.getPseudoClassStates().contains(PseudoClass.getPseudoClass("horizontal"))))
                    .lookup(".decrement-button"), () -> scroll(HorizontalDirection.LEFT));
            //scroll(HorizontalDirection.LEFT);
        }
        for (int i = 0; i < 100 && TestUtil.fx(() -> target.localToScreen(target.getBoundsInLocal()).getMaxY()) > viewBounds.getMaxY(); i++)
        {
            System.out.println("Scrolling DOWN");
            //scroll(VerticalDirection.DOWN);
            clickOrScroll(from(enclosingScroll)
                    .lookup(".scroll-bar")
                    .match((ScrollBar sb) -> TestUtil.fx(() -> sb.getPseudoClassStates().contains(PseudoClass.getPseudoClass("vertical"))))
                    .lookup(".increment-button"), () -> scroll(SystemUtils.IS_OS_MAC_OSX ? VerticalDirection.UP : VerticalDirection.DOWN));
        }
        for (int i = 0; i < 100 && TestUtil.fx(() -> target.localToScreen(target.getBoundsInLocal()).getMinY()) < viewBounds.getMinY(); i++)
        {
            System.out.println("Scrolling UP");
            //scroll(VerticalDirection.UP);
            clickOrScroll(from(enclosingScroll)
                    .lookup(".scroll-bar")
                    .match((ScrollBar sb) -> TestUtil.fx(() -> sb.getPseudoClassStates().contains(PseudoClass.getPseudoClass("vertical"))))
                    .lookup(".decrement-button"), () -> scroll(SystemUtils.IS_OS_MAC_OSX ? VerticalDirection.DOWN : VerticalDirection.UP));
        }
        Bounds targetScreenBounds = TestUtil.fx(() -> target.localToScreen(target.getBoundsInLocal()));
        assertTrue("View bounds: " + viewBounds + " target: " + targetScreenBounds, viewBounds.contains(targetScreenBounds));
    }
    
    @OnThread(Tag.Any)
    default void keyboardMoveTo(VirtualGrid virtualGrid, CellPosition target)
    {
        Log.debug("Moving to position " + target);
        
        int pageHeight = TestUtil.fx(() -> virtualGrid.calcPageHeight());
        
        push(KeyCode.CONTROL, KeyCode.HOME);
        // First go to correct row:
        for (int i = 0; i < target.rowIndex / pageHeight; i++)
        {
            push(KeyCode.PAGE_DOWN);
        }
        for (int i = 0; i < target.rowIndex % pageHeight; i++)
        {
            push(KeyCode.DOWN);
        }
        
        while (TestUtil.fx(() -> virtualGrid._test_getSelection().map(s -> s.positionToEnsureInView().columnIndex).orElse(Integer.MAX_VALUE)) < target.columnIndex)
        {
            push(KeyCode.RIGHT);
        }
        // Wait for smooth scroll to finish:
        TestUtil.sleep(300);
    }
    
    // Ideally, will be private in later Java:
    @OnThread(Tag.Any)
    default public void clickOrScroll(NodeQuery nodeQuery, Runnable scroll)
    {
        Node scrollButton = nodeQuery.tryQuery().orElse(null);
        if (scrollButton != null)
            clickOn(scrollButton, MouseButton.PRIMARY);
        else
            scroll.run();
    }
}
