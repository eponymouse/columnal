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

import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.testjavafx.FxRobotInterface;
import test.gui.TFXUtil;
import xyz.columnal.data.CellPosition;
import xyz.columnal.id.TableId;
import xyz.columnal.data.TableManager;
import xyz.columnal.error.UserException;
import xyz.columnal.gui.grid.RectangleBounds;
import xyz.columnal.gui.grid.VirtualGrid;
import threadchecker.OnThread;
import threadchecker.Tag;

public interface ClickOnTableHeaderTrait extends FxRobotInterface, ScrollToTrait, ClickTableLocationTrait
{
    @OnThread(Tag.Any)
    public default FxRobotInterface triggerTableHeaderContextMenu(VirtualGrid virtualGrid, TableManager tableManager, TableId id) throws UserException
    {
        return triggerTableHeaderContextMenu(virtualGrid, TFXUtil.tablePosition(tableManager, id));
    }
    
    @OnThread(Tag.Any)
    public default FxRobotInterface triggerTableHeaderContextMenu(VirtualGrid virtualGrid, CellPosition position) throws UserException
    {
        keyboardMoveTo(virtualGrid, position);
        
        Node tableNameField = withItemInBounds(".table-display-table-title .table-name-text-field",
            virtualGrid, new RectangleBounds(position, position), (n, p) -> {});
        if (tableNameField == null)
            throw new RuntimeException("Could not find table name field for " + position);
        @SuppressWarnings("nullness")
        Node tableHeader = TFXUtil.fx(() -> tableNameField.getParent());
        Bounds tableHeaderBounds = TFXUtil.fx(() -> tableHeader.localToScreen(tableHeader.getBoundsInLocal()));
        return showContextMenu(tableHeader, new Point2D(tableHeaderBounds.getMinX() + 1, tableHeaderBounds.getMinY() + 2));
    }
    
    // Matches method in FXApplicationTest:
    @OnThread(Tag.Any)
    public FxRobotInterface showContextMenu(Node node, @Nullable Point2D pointOnScreen);
}
