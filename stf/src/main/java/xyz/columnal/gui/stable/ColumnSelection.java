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

import xyz.columnal.data.CellPosition;
import xyz.columnal.utility.Utility;

public class ColumnSelection //implements CellSelection
{
    /*
    private final int anchorColumn;
    private final int columnIndex;

    public ColumnSelection(int anchorColumn, int columnIndex)
    {
        this.anchorColumn = anchorColumn;
        this.columnIndex = columnIndex;
    }

    @Override
    public CellSelection atHome(boolean extendSelection)
    {
        return new ColumnSelection(extendSelection ? columnIndex : 0, 0);
    }

    @Override
    public CellSelection atEnd(boolean extendSelection, int maxRows, int maxColumns)
    {
        return new ColumnSelection(extendSelection ? columnIndex : maxColumns - 1, maxColumns - 1);
    }

    @Override
    public CellSelection move(boolean extendSelection, int byRows, int byColumns, int maxRows, int maxColumns)
    {
        if (byRows == 0)
        {
            int dest = Utility.clampIncl(0, columnIndex + byColumns, maxColumns - 1);
            return new ColumnSelection(extendSelection ? anchorColumn : dest, dest);
        }
        else
        {
            // Select leftmost cell
            return new RectangularCellSelection(0, columnIndex);
        }
    }

    @Override
    public SelectionStatus selectionStatus(CellPosition cellPosition)
    {
        return columnSelectionStatus(cellPosition.columnIndex);
    }

    @Override
    public SelectionStatus columnSelectionStatus(int index)
    {
        int minRow = Math.min(columnIndex, anchorColumn);
        int maxRow = Math.max(columnIndex, anchorColumn);
        if (index == columnIndex)
            return SelectionStatus.PRIMARY_SELECTION;
        else if (minRow <= index && index <= maxRow)
            return SelectionStatus.SECONDARY_SELECTION;
        else
            return SelectionStatus.UNSELECTED;
    }

    @Override
    public SelectionStatus rowSelectionStatus(int rowIndex)
    {
        return SelectionStatus.UNSELECTED;
    }
    */
}
