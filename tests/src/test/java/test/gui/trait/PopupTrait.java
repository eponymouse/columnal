package test.gui.trait;

import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.input.MouseButton;
import javafx.stage.PopupWindow;
import javafx.stage.Window;
import xyz.columnal.log.Log;
import org.controlsfx.control.PopOver;
import org.testfx.api.FxRobotInterface;
import org.testfx.service.query.PointQuery;
import test.TestUtil;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Utility;

import java.util.function.Supplier;

public interface PopupTrait extends FxRobotInterface
{
    @OnThread(Tag.Any)
    default public void moveAndDismissPopupsAtPos(PointQuery pointQuery)
    {
        Point2D p = pointQuery.query();
        Supplier<Boolean> popupAtMousePos = () -> {
            return TestUtil.fx(() -> {
                return Utility.filterClass(listWindows().stream(), PopupWindow.class).anyMatch(w -> {
                    return new Rectangle2D(w.getX() - 1, w.getY() - 1, w.getWidth() + 2, w.getHeight() + 2).contains(p);
                });
            });
        };
        moveTo(p);
        // Popup windows don't seem to report their locations accurately (on JavaFX 8), and I can't find
        // a work-around for this, so just speculatively middle click once anyway.
        // Ideally, middle clicking does nothing on non-popups so this should always work:
        clickOn(MouseButton.MIDDLE);
        int attempts = 0;
        while (popupAtMousePos.get() && ++attempts < 10)
        {
            System.out.println("Middle clicking to dismiss popup");
            clickOn(MouseButton.MIDDLE);
            TestUtil.delay(50);
        }
    }
    
}
