/*
 * Columnal: Safer, smoother data table processing.
 * Copyright (c) Neil Brown, 2016-2020, 2022.
 *
 * This file is part of Columnal.
 *
 * Columnal is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Columnal is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for 
 * more details.
 *
 * You should have received a copy of the GNU General Public License along 
 * with Columnal. If not, see <https://www.gnu.org/licenses/>.
 */

package test.gui.trait;

import org.testjavafx.node.NodeQuery;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.input.MouseButton;
import test.gui.TFXUtil;
import xyz.columnal.log.Log;
import org.testjavafx.FxRobotInterface;
import xyz.columnal.gui.grid.RectangleBounds;
import xyz.columnal.gui.grid.VirtualGrid;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.function.fx.FXPlatformBiConsumer;
import xyz.columnal.utility.gui.FXUtility;

import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public interface ClickTableLocationTrait extends FxRobotInterface
{
    @OnThread(Tag.Any)
    public default Node clickOnItemInBounds(String nodeQuery, VirtualGrid virtualGrid, RectangleBounds rectangleBounds, MouseButton... buttons)
    {
        return withItemInBounds(nodeQuery, virtualGrid, rectangleBounds, (n, p) -> clickOn(p, buttons));
    }
    @OnThread(Tag.Any)
    public default Node clickOnItemInBounds(NodeQuery nodeQuery, VirtualGrid virtualGrid, RectangleBounds rectangleBounds, MouseButton... buttons)
    {
        return withItemInBounds(nodeQuery, virtualGrid, rectangleBounds, (n, p) -> clickOn(p, buttons));
    }
    @OnThread(Tag.Any)
    public default Node withItemInBounds(String nodeQuery, VirtualGrid virtualGrid, RectangleBounds rectangleBounds, FXPlatformBiConsumer<Node, Point2D> action)
    {
        return withItemInBounds(TFXUtil.fx(() -> lookup(nodeQuery)), virtualGrid, rectangleBounds, action);
    }
    
    @OnThread(Tag.Any)
    public default Node withItemInBounds(NodeQuery nodeQuery, VirtualGrid virtualGrid, RectangleBounds rectangleBounds, FXPlatformBiConsumer<Node, Point2D> action)
    {
        Bounds theoretical = TFXUtil.fx(() -> virtualGrid.getRectangleBoundsScreen(rectangleBounds));
        // We shrink by two pixels in each direction to avoid
        // picking up neighbouring cells, which will exactly
        // border us:
        Bounds box = new BoundingBox(theoretical.getMinX() + 2.0, theoretical.getMinY() + 2.0, theoretical.getWidth() - 4.0, theoretical.getHeight() - 4.0);
        
        Log.debug("Bounds for " + rectangleBounds + " on screen are " + box);
        
        Set<Node> target = nodeQuery.filter((Predicate<Node>)(node -> {
            return TFXUtil.fx(() -> box.intersects(node.localToScreen(node.getBoundsInLocal())));
        })).queryAll(); 
        if (target.isEmpty())
            throw new RuntimeException("Could not find node at given position, looked for " + box + " among " + nodeQuery.queryAll().stream().map(n -> TFXUtil.fx(() -> n.localToScreen(n.getBoundsInLocal()))).collect(Collectors.toList()));
        if (target.size() > 1) 
            throw new RuntimeException("Found multiple nodes at given position:" + target.stream().map(t -> TFXUtil.fx(() -> t.toString() + " " +  t.localToScreen(t.getBoundsInLocal()))).collect(Collectors.joining("\n")));
        Node targetNN = target.iterator().next();
        Log.debug("Found target " + targetNN + " at " + TFXUtil.fx(() -> targetNN.localToScreen(targetNN.getBoundsInLocal())));
        Rectangle2D intersect = TFXUtil.fx(() -> FXUtility.intersectRect(FXUtility.boundsToRect(targetNN.localToScreen(targetNN.getBoundsInLocal())), FXUtility.boundsToRect(box)));
        Point2D centre = FXUtility.getCentre(intersect);
        TFXUtil.fx_(() -> action.consume(targetNN, centre));
        return targetNN;
    }
}
