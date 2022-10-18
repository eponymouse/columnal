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

package xyz.columnal.gui.grid;

import javafx.geometry.BoundingBox;
import javafx.geometry.Point2D;
import javafx.scene.shape.Rectangle;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.CellPosition;
import xyz.columnal.gui.grid.VirtualGridSupplier.ItemState;
import xyz.columnal.gui.grid.VirtualGridSupplier.ViewOrder;
import xyz.columnal.gui.grid.VirtualGridSupplier.VisibleBounds;
import xyz.columnal.gui.grid.VirtualGridSupplierFloating.FloatingItem;
import xyz.columnal.styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.gui.ResizableRectangle;

import java.util.Optional;

/**
 * A helper class for FloatingItems which are simple rectangle shapes.
 * 
 * This implements the positioning conversion from cells to pixels, and implements
 * makeCell to delegate the relevant setup to styleNewRectangle.
 */
@OnThread(Tag.FXPlatform)
public abstract class RectangleOverlayItem extends FloatingItem<ResizableRectangle>
{
    protected RectangleOverlayItem(ViewOrder viewOrder)
    {
        super(viewOrder);
    }

    @Override
    public final Optional<BoundingBox> calculatePosition(VisibleBounds visibleBounds)
    {
        return calculateBounds(visibleBounds)
            .flatMap(e -> e.either(b -> Optional.of(b), 
                r -> visibleBounds.clampVisible(r).map(bounds -> calculateBoundingBox(visibleBounds, bounds))));
    }

    protected BoundingBox calculateBoundingBox(VisibleBounds visibleBounds, RectangleBounds bounds)
    {
        double left = visibleBounds.getXCoord(bounds.topLeftIncl.columnIndex);
        double top = visibleBounds.getYCoord(bounds.topLeftIncl.rowIndex);
        // Take one pixel off so that we are on top of the right/bottom divider inset
        // rather than showing it just inside the rectangle (which looks weird)
        double right = visibleBounds.getXCoordAfter(bounds.bottomRightIncl.columnIndex) - 1;
        double bottom = visibleBounds.getYCoordAfter(bounds.bottomRightIncl.rowIndex) - 1;

        return new BoundingBox(
                left, top, right - left, bottom - top
        );
    }

    /**
     * Note that if right is returned, clampVisible will be called for you before converting to a bounding box.
     * @param visibleBounds
     * @return
     */
    protected abstract Optional<Either<BoundingBox, RectangleBounds>> calculateBounds(VisibleBounds visibleBounds);

    @Override
    public final ResizableRectangle makeCell(VisibleBounds visibleBounds)
    {
        ResizableRectangle r = new ResizableRectangle();
        r.setMouseTransparent(true);
        styleNewRectangle(r, visibleBounds);
        return r;
    }

    protected abstract void styleNewRectangle(Rectangle r, VisibleBounds visibleBounds);

    @Override
    public final @Nullable Pair<ItemState, @Nullable StyledString> getItemState(CellPosition cellPosition, Point2D screenPos)
    {
        return null;
    }

    @Override
    public void keyboardActivate(CellPosition cellPosition)
    {
        // Do nothing -- we're only an overlay
    }
}
