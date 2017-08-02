package test.gui;

import javafx.geometry.Bounds;
import javafx.geometry.HorizontalDirection;
import javafx.geometry.VerticalDirection;
import javafx.scene.Node;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import org.testfx.api.FxRobotInterface;
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
        Node target = lookup(nodeLocator).query();
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
}
