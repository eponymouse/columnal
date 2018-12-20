package test.gui.trait;

import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.input.MouseButton;
import javafx.stage.PopupWindow;
import org.testfx.api.FxRobotInterface;
import org.testfx.service.query.PointQuery;
import test.TestUtil;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

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
                    return new Rectangle2D(w.getX(), w.getY(), w.getWidth(), w.getHeight()).contains(p);
                });
            });
        };
        moveTo(p);
        int attempts = 0;
        while (popupAtMousePos.get() && ++attempts < 10)
        {
            clickOn(MouseButton.MIDDLE);
            TestUtil.delay(50);
        }
    }
    
}
