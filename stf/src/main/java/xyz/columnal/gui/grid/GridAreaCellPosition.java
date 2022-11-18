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

import annotation.units.AbsColIndex;
import annotation.units.AbsRowIndex;
import annotation.units.GridAreaColIndex;
import annotation.units.GridAreaRowIndex;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.CellPosition;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * A grid area cell position is a location in a particular subgrid (e.g. table)
 */
/* package-visible */
@OnThread(Tag.Any)
public class GridAreaCellPosition
{
    // Both are zero-based:
    public final @GridAreaRowIndex int rowIndex;
    public final @GridAreaColIndex int columnIndex;

    public GridAreaCellPosition(@GridAreaRowIndex int rowIndex, @GridAreaColIndex int columnIndex)
    {
        this.rowIndex = rowIndex;
        this.columnIndex = columnIndex;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GridAreaCellPosition that = (GridAreaCellPosition) o;

        if (rowIndex != that.rowIndex) return false;
        return columnIndex == that.columnIndex;
    }

    @Override
    public int hashCode()
    {
        int result = rowIndex;
        result = 31 * result + columnIndex;
        return result;
    }

    @Override
    public String toString()
    {
        return "(" + columnIndex + ", " + rowIndex + ")";
    }

    @SuppressWarnings("units")
    public static GridAreaCellPosition relativeFrom(CellPosition position, CellPosition topLeft)
    {
        return new GridAreaCellPosition(position.rowIndex - topLeft.rowIndex, position.columnIndex - topLeft.columnIndex);
    }

    /**
     * Given a top-left position for the grid area, returns the overall position once you apply
     * the offset given by this position.
     */
    public CellPosition from(CellPosition position)
    {
        return position.offsetByRowCols(rowIndex, columnIndex);
    }

    /*
    @SuppressWarnings("units")
    public GridAreaCellPosition offsetByRowCols(int rows, int cols)
    {
        return new GridAreaCellPosition(rowIndex + rows, columnIndex + cols);
    }
    
    public static final GridAreaCellPosition ORIGIN = new GridAreaCellPosition(row(0), col(0)); 

    @SuppressWarnings("units")
    public static @GridAreaRowIndex int row(int row)
    {
        return row;
    }

    @SuppressWarnings("units")
    public static @GridAreaColIndex int col(int col)
    {
        return col;
    }
    */
}
