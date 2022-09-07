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

package xyz.columnal.data;

import annotation.units.TableDataColIndex;
import annotation.units.TableDataRowIndex;
import org.checkerframework.checker.nullness.qual.Nullable;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * A cell position is a location in the overall grid, independent
 * of any individual table bounds.
 */
/* package-visible */
@OnThread(Tag.Any)
public class DataItemPosition
{
    // Both are zero-based:
    public final @TableDataRowIndex int rowIndex;
    public final @TableDataColIndex int columnIndex;

    public DataItemPosition(@TableDataRowIndex int rowIndex, @TableDataColIndex int columnIndex)
    {
        this.rowIndex = rowIndex;
        this.columnIndex = columnIndex;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DataItemPosition that = (DataItemPosition) o;

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
    /*
    @SuppressWarnings("units")
    public DataItemPosition offsetByRowCols(int rows, int cols)
    {
        return new DataItemPosition(rowIndex + rows, columnIndex + cols);
    }
    */
    
    @SuppressWarnings("units")
    public static @TableDataRowIndex int row(int row)
    {
        return row;
    }

    @SuppressWarnings("units")
    public static @TableDataColIndex int col(int col)
    {
        return col;
    }
}
