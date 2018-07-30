package test.gui;

import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.input.MouseButton;
import log.Log;
import org.testfx.api.FxRobotInterface;
import org.testfx.service.query.NodeQuery;
import records.gui.grid.RectangleBounds;
import records.gui.grid.VirtualGrid;
import test.TestUtil;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.gui.FXUtility;

import java.util.function.Predicate;
import java.util.stream.Collectors;

public interface ClickTableLocationTrait extends FxRobotInterface
{
    @OnThread(Tag.Simulation)
    public default void clickOnItemInBounds(NodeQuery nodeQuery, VirtualGrid virtualGrid, RectangleBounds rectangleBounds, MouseButton... buttons)
    {
        Bounds box = TestUtil.fx(() -> virtualGrid.getRectangleBoundsScreen(rectangleBounds));
        Log.debug("Bounds for " + rectangleBounds + " on screen are " + box);
        
        Node target = nodeQuery.lookup((Predicate<Node>)(node -> {
            return TestUtil.fx(() -> box.intersects(node.localToScreen(node.getBoundsInLocal())));
        })).query(); 
        if (target == null)
            throw new RuntimeException("Could not find node at given position, looked for " + box + " among " + nodeQuery.queryAll().stream().map(n -> TestUtil.fx(() -> n.localToScreen(n.getBoundsInLocal()))).collect(Collectors.toList()));
        Node targetNN = target;
        Log.debug("Found target " + targetNN + " at " + TestUtil.fx(() -> targetNN.localToScreen(targetNN.getBoundsInLocal())));
        Rectangle2D intersect = TestUtil.fx(() -> FXUtility.intersectRect(FXUtility.boundsToRect(targetNN.localToScreen(targetNN.getBoundsInLocal())), FXUtility.boundsToRect(box)));
        Point2D centre = FXUtility.getCentre(intersect);
        Log.debug("Clicking " + centre);
        clickOn(centre, buttons);
    }
}
