package test.gui;

import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.input.MouseButton;
import org.testfx.api.FxRobotInterface;
import org.testfx.service.query.PointQuery;
import test.TestUtil;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.gui.FXUtility;

import java.util.function.Supplier;

public interface PopupTrait extends FxRobotInterface
{
    @OnThread(Tag.Any)
    default public void moveAndDismissPopupsAtPos(PointQuery pointQuery)
    {
        Supplier<Boolean> popupAtMousePos = () -> {
            return TestUtil.fx(() -> {
                Point2D p = pointQuery.getPosition();
                return listWindows().stream().anyMatch(w -> {
                    return new Rectangle2D(w.getX(), w.getY(), w.getWidth(), w.getHeight()).contains(p);
                });
            });
        };
        moveTo(pointQuery);
        while (popupAtMousePos.get())
        {
            clickOn(MouseButton.MIDDLE);
        }
    }
    
}
