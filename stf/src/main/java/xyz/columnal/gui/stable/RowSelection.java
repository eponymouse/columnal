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

package xyz.columnal.gui.stable;

import xyz.columnal.utility.Utility;

public class RowSelection //implements CellSelection
{
    /*
    private final int anchorRow;
    private final int rowIndex;

    public RowSelection(int anchorRow, int rowIndex)
    {
        this.anchorRow = anchorRow;
        this.rowIndex = rowIndex;
    }

    @Override
    public CellSelection atHome(boolean extendSelection)
    {
        return new RowSelection(extendSelection ? rowIndex : 0, 0);
    }

    @Override
    public CellSelection atEnd(boolean extendSelection, int maxRows, int maxColumns)
    {
        return new RowSelection(extendSelection ? rowIndex : maxRows - 1, maxRows - 1);
    }

    @Override
    public CellSelection move(boolean extendSelection, int byRows, int byColumns, int maxRows, int maxColumns)
    {
        if (byColumns == 0)
        {
            int dest = Utility.clampIncl(0, rowIndex + byRows, maxRows - 1);
            return new RowSelection(extendSelection ? anchorRow : dest, dest);
        }
        else
        {
            // Select leftmost cell
            return new RectangularCellSelection(rowIndex, 0);
        }
    }

    @Override
    public SelectionStatus rowSelectionStatus(int index)
    {
        int minRow = Math.min(rowIndex, anchorRow);
        int maxRow = Math.max(rowIndex, anchorRow);
        if (index == rowIndex)
            return SelectionStatus.PRIMARY_SELECTION;
        else if (minRow <= index && index <= maxRow)
            return SelectionStatus.SECONDARY_SELECTION;
        else
            return SelectionStatus.UNSELECTED;
    }

    @Override
    public SelectionStatus columnSelectionStatus(int columnIndex)
    {
        return SelectionStatus.UNSELECTED;
    }
    
    */
}
