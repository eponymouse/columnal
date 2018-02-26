package test.gui;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import org.testfx.api.FxRobotInterface;
import org.testfx.service.query.NodeQuery;
import records.gui.grid.RectangleBounds;
import records.gui.grid.VirtualGrid;

import java.util.function.Predicate;
import java.util.stream.Collectors;

public interface ClickTableLocationTrait extends FxRobotInterface
{
    public default void clickOnItemInBounds(NodeQuery nodeQuery, VirtualGrid virtualGrid, RectangleBounds rectangleBounds)
    {
        Bounds box = virtualGrid._test_getRectangleBoundsScreen(rectangleBounds);
        
        Node target = nodeQuery.lookup((Predicate<Node>)(node -> {
            return box.intersects(node.localToScreen(node.getBoundsInLocal()));
        })).query(); 
        if (target == null)
            throw new RuntimeException("Could not find node at given position, looked for " + box + " among " + nodeQuery.queryAll().stream().map(n -> n.localToScreen(n.getBoundsInLocal())).collect(Collectors.toList()));
        clickOn(target);
    }
}
