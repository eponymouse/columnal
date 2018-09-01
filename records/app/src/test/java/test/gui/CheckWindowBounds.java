package test.gui;

import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Window;
import org.testfx.api.FxRobotInterface;
import test.TestUtil;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.Assert.assertEquals;

public interface CheckWindowBounds extends FxRobotInterface
{
    @OnThread(Tag.Any)
    public default void checkWindowWithinScreen()
    {
        for (Window window : listWindows())
        {
            double windowX = TestUtil.fx(() -> window.getX());
            double windowY = TestUtil.fx(() -> window.getY());
            List<Screen> screens = TestUtil.fx(() -> Screen.getScreensForRectangle(windowX, windowY, window.getWidth(), window.getHeight()));
            assertEquals(1, screens.size());
            Screen screen = screens.get(0);
            Rectangle2D screenBounds = TestUtil.fx(() -> screen.getVisualBounds());
            assertThat(windowX, greaterThanOrEqualTo(screenBounds.getMinX()));
            assertThat(windowY, greaterThanOrEqualTo(screenBounds.getMinY()));
            assertThat(windowX + TestUtil.fx(() -> window.getWidth()), lessThanOrEqualTo(screenBounds.getMaxX()));
            assertThat(windowY + TestUtil.fx(() -> window.getHeight()), lessThanOrEqualTo(screenBounds.getMaxY()));
        }
    }
}
