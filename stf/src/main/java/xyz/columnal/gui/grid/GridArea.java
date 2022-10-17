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

import annotation.units.AbsRowIndex;
import annotation.units.GridAreaRowIndex;
import xyz.columnal.log.Log;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.CellPosition;
import xyz.columnal.error.InternalException;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.function.fx.FXPlatformConsumer;
import xyz.columnal.utility.function.fx.FXPlatformFunction;
import xyz.columnal.utility.function.fx.FXPlatformRunnable;
import xyz.columnal.utility.Utility;

import java.util.Optional;

/**
 * One rectangular table area within a parent VirtualGrid.  Tracks position,
 * size, adjusts when resized.
 * 
 * Overlays such as line numbers are not included in the logical bounds,
 * but column headers are included.
 * 
 * No two GridArea items may overlap, and VirtualGrid will reposition to make sure
 * this is always true.
 * 
 * Each GridArea is assumed to have a fixed immediately-knowable position and number of columns,
 * but a number of rows which may not be known ahead of time.
 */
@OnThread(Tag.FXPlatform)
public abstract class GridArea
{
    // The top left cell, which is probably a column header.
    private CellPosition topLeft;
    private CellPosition bottomRight;
    
    private @MonotonicNonNull VirtualGrid parent;

    public GridArea()
    {
        // Default position:
        this.topLeft = new CellPosition(CellPosition.row(1), CellPosition.col(1));
        this.bottomRight = topLeft;
    }

    public final CellPosition getPosition(@UnknownInitialization(GridArea.class) GridArea this)
    {
        return topLeft;
    }
    
    public void setPosition(@UnknownInitialization(GridArea.class) GridArea this, CellPosition cellPosition)
    {
        bottomRight = bottomRight.offsetByRowCols(cellPosition.rowIndex - topLeft.rowIndex, cellPosition.columnIndex - topLeft.columnIndex);
        topLeft = cellPosition;
        updateParent();
    }

    protected void updateParent(@UnknownInitialization(GridArea.class) GridArea this)
    {
        if (parent != null)
            parent.positionOrAreaChanged();
    }
    
    // Calls the consumer, iff we have a non-null parent
    protected void withParent_(@UnknownInitialization(GridArea.class) GridArea this, FXPlatformConsumer<VirtualGrid> withVirtualGrid)
    {
        if (parent != null)
            withVirtualGrid.consume(parent);
    }

    protected <R> Optional<R> withParent(@UnknownInitialization(GridArea.class) GridArea this, FXPlatformFunction<VirtualGrid, @NonNull R> withVirtualGrid)
    {
        if (parent != null)
            return Optional.of(withVirtualGrid.apply(parent));
        else
            return Optional.empty();
    }

    public final void addedToGrid(VirtualGrid parent)
    {
        this.parent = parent;
    }

    public final VirtualGrid _test_getParent()
    {
        if (parent == null)
            throw new RuntimeException("GridArea " + this + " has no parent");
        return parent;
    }

    /**
     * Check if more rows are available, up to and including the given row number (but not beyond).
     * If you need to do a calculation off-thread, and find that you do have a new size
     * (i.e. different to the one you return from the method), call the runnable
     * @param checkUpToRowIncl The row to check up to in overall grid position
     * @param updateSizeAndPositions The runnable to call if the size later changes.
     * @return The current known row size.
     */
    @OnThread(Tag.FXPlatform)
    protected abstract void updateKnownRows(@GridAreaRowIndex int checkUpToRowIncl, FXPlatformRunnable updateSizeAndPositions);

    public final @AbsRowIndex int getAndUpdateBottomRow(@AbsRowIndex int checkUpToRowIncl, FXPlatformRunnable updateSizeAndPositions)
    {
        @SuppressWarnings("units")
        @GridAreaRowIndex int gridAreaRow = checkUpToRowIncl - getPosition().rowIndex;
        updateKnownRows(gridAreaRow, updateSizeAndPositions);
        bottomRight = recalculateBottomRightIncl();
        if (bottomRight.rowIndex < topLeft.rowIndex || bottomRight.columnIndex < topLeft.columnIndex)
        {
            try
            {
                throw new InternalException("Bottom right " + bottomRight + " is left/above the top left: " + topLeft);
            }
            catch (InternalException e)
            {
                Log.log(e);
            }
            // For safety:
            bottomRight = new CellPosition(Utility.maxRow(topLeft.rowIndex, bottomRight.rowIndex), Utility.maxCol(topLeft.columnIndex, bottomRight.columnIndex));
        }
        return bottomRight.rowIndex;
    }

    protected abstract CellPosition recalculateBottomRightIncl();

    public final boolean contains(@UnknownInitialization(GridArea.class) GridArea this, CellPosition cellPosition)
    {
        return topLeft.rowIndex <= cellPosition.rowIndex && cellPosition.rowIndex <= bottomRight.rowIndex
            && topLeft.columnIndex  <= cellPosition.columnIndex && cellPosition.columnIndex <= bottomRight.columnIndex;
    }
    
    public final CellPosition getBottomRightIncl(@UnknownInitialization(GridArea.class) GridArea this)
    {
        return bottomRight;
    }
    
    // Select a cell by moving to it using the keyboard/mouse.  Return null if not possible
    public abstract @Nullable CellSelection getSelectionForSingleCell(CellPosition cellPosition);

    // When sorting grid areas, ones with a lower sort key will be put to the left.
    public abstract String getSortKey();
}
