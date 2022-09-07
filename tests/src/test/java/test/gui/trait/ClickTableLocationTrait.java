package test.gui.trait;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.input.MouseButton;
import xyz.columnal.log.Log;
import org.testfx.api.FxRobotInterface;
import org.testfx.service.query.NodeQuery;
import xyz.columnal.gui.grid.RectangleBounds;
import xyz.columnal.gui.grid.VirtualGrid;
import test.TestUtil;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.FXPlatformBiConsumer;
import xyz.columnal.utility.gui.FXUtility;

import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public interface ClickTableLocationTrait extends FxRobotInterface
{
    @OnThread(Tag.Any)
    public default Node clickOnItemInBounds(NodeQuery nodeQuery, VirtualGrid virtualGrid, RectangleBounds rectangleBounds, MouseButton... buttons)
    {
        return withItemInBounds(nodeQuery, virtualGrid, rectangleBounds, (n, p) -> clickOn(p, buttons));
    }
    
    @OnThread(Tag.Any)
    public default Node withItemInBounds(NodeQuery nodeQuery, VirtualGrid virtualGrid, RectangleBounds rectangleBounds, FXPlatformBiConsumer<Node, Point2D> action)
    {
        Bounds theoretical = TestUtil.fx(() -> virtualGrid.getRectangleBoundsScreen(rectangleBounds));
        // We shrink by two pixels in each direction to avoid
        // picking up neighbouring cells, which will exactly
        // border us:
        Bounds box = new BoundingBox(theoretical.getMinX() + 2.0, theoretical.getMinY() + 2.0, theoretical.getWidth() - 4.0, theoretical.getHeight() - 4.0);
        
        Log.debug("Bounds for " + rectangleBounds + " on screen are " + box);
        
        Set<Node> target = nodeQuery.match((Predicate<Node>)(node -> {
            return TestUtil.fx(() -> box.intersects(node.localToScreen(node.getBoundsInLocal())));
        })).queryAll(); 
        if (target.isEmpty())
            throw new RuntimeException("Could not find node at given position, looked for " + box + " among " + nodeQuery.queryAll().stream().map(n -> TestUtil.fx(() -> n.localToScreen(n.getBoundsInLocal()))).collect(Collectors.toList()));
        if (target.size() > 1) 
            throw new RuntimeException("Found multiple nodes at given position:" + target.stream().map(t -> TestUtil.fx(() -> t.toString() + " " +  t.localToScreen(t.getBoundsInLocal()))).collect(Collectors.joining("\n")));
        Node targetNN = target.iterator().next();
        Log.debug("Found target " + targetNN + " at " + TestUtil.fx(() -> targetNN.localToScreen(targetNN.getBoundsInLocal())));
        Rectangle2D intersect = TestUtil.fx(() -> FXUtility.intersectRect(FXUtility.boundsToRect(targetNN.localToScreen(targetNN.getBoundsInLocal())), FXUtility.boundsToRect(box)));
        Point2D centre = FXUtility.getCentre(intersect);
        TestUtil.fx_(() -> action.consume(targetNN, centre));
        return targetNN;
    }
}
