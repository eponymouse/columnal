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
import javafx.stage.Window;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.CellPosition;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.Utility;

/**
 * A rectangular selection is constrained to one table
 * (TODO and its adjacent transforms?)
 * Immutable.
 */
@OnThread(Tag.FXPlatform)
public class RectangularTableCellSelection implements CellSelection
{
    private final CellPosition startAnchor;
    private final CellPosition curFocus;

    private final TableSelectionLimits tableSelectionLimits; 


    // Selects a single cell:
    public RectangularTableCellSelection(@AbsRowIndex int rowIndex, @AbsColIndex int columnIndex, TableSelectionLimits tableSelectionLimits)
    {
        startAnchor = new CellPosition(rowIndex, columnIndex);
        curFocus = startAnchor;
        this.tableSelectionLimits = tableSelectionLimits;
    }

    private RectangularTableCellSelection(CellPosition anchor, CellPosition focus, TableSelectionLimits tableSelectionLimits)
    {
        this.startAnchor = anchor;
        this.curFocus = focus;
        this.tableSelectionLimits = tableSelectionLimits;
    }

    @Override
    public void doCopy()
    {
        tableSelectionLimits.doCopy(calcTopLeftIncl(), calcBottomRightIncl());
    }

    @Override
    public void doPaste()
    {
        // TODO paste data over the current cells, offering to extend
        // the table if needed.
    }

    @Override
    public void doDelete()
    {
        tableSelectionLimits.doDelete(calcTopLeftIncl(), calcBottomRightIncl());
    }

    @Override
    public CellPosition getActivateTarget()
    {
        return curFocus;
    }

    @Override
    public CellSelection atHome(boolean extendSelection)
    {
        CellPosition dest = new CellPosition(tableSelectionLimits.getTopLeftIncl().rowIndex, curFocus.columnIndex);
        return new RectangularTableCellSelection(extendSelection ? startAnchor : dest, dest, tableSelectionLimits);
    }

    @Override
    public CellSelection atEnd(boolean extendSelection)
    {
        CellPosition dest = new CellPosition(tableSelectionLimits.getBottomRightIncl().rowIndex, curFocus.columnIndex);
        return new RectangularTableCellSelection(extendSelection ? startAnchor : dest, dest, tableSelectionLimits);
    }

    @Override
    public Either<CellPosition, CellSelection> move(boolean extendSelection, int _byRows, int _byColumns)
    {
        @AbsRowIndex int byRows = CellPosition.row(_byRows);
        @AbsColIndex int byColumns = CellPosition.col(_byColumns);
        @AbsRowIndex int targetRow = curFocus.rowIndex + byRows;
        @AbsRowIndex int clampedRow = Utility.maxRow(tableSelectionLimits.getTopLeftIncl().rowIndex, Utility.minRow(tableSelectionLimits.getBottomRightIncl().rowIndex, targetRow));
        @AbsColIndex int targetColumn = curFocus.columnIndex + byColumns;
        @AbsColIndex int clampedColumn = Utility.maxCol(tableSelectionLimits.getTopLeftIncl().columnIndex, Utility.minCol(tableSelectionLimits.getBottomRightIncl().columnIndex, targetColumn));
        
        // If we're trying to move outside without holding shift, do so:
        if ((clampedRow != targetRow || clampedColumn != targetColumn) && !extendSelection)
        {
            return Either.left(new CellPosition(targetRow, targetColumn));
        }
        else
        {
            // If we are all within bounds, or doing shift-selection, we stay in the table:
            CellPosition dest = new CellPosition(clampedRow, clampedColumn);
            // Move from top-left:
            return Either.right(new RectangularTableCellSelection(extendSelection ? startAnchor : dest, dest, tableSelectionLimits));
        }
    }

    @Override
    public @Nullable CellSelection extendTo(CellPosition pos)
    {
        CellPosition topLeft = tableSelectionLimits.getTopLeftIncl();
        CellPosition bottomRight = tableSelectionLimits.getBottomRightIncl();
        if (new RectangleBounds(topLeft, bottomRight).contains(pos))
        {
            // It's possible
            return new RectangularTableCellSelection(startAnchor, pos, tableSelectionLimits);
        }
        else
            return null;
    }

    @Override
    public CellPosition positionToEnsureInView()
    {
        return curFocus;
    }

    @Override
    public RectangleBounds getSelectionDisplayRectangle()
    {
        return new RectangleBounds(
                calcTopLeftIncl(),
                calcBottomRightIncl()
        );
    }

    public CellPosition calcBottomRightIncl()
    {
        return new CellPosition(
            Utility.maxRow(startAnchor.rowIndex, curFocus.rowIndex),
            Utility.maxCol(startAnchor.columnIndex, curFocus.columnIndex)
        );
    }

    public CellPosition calcTopLeftIncl()
    {
        return new CellPosition(
            Utility.minRow(startAnchor.rowIndex, curFocus.rowIndex),
            Utility.minCol(startAnchor.columnIndex, curFocus.columnIndex)
        );
    }

    @Override
    public boolean isExactly(CellPosition cellPosition)
    {
        return startAnchor.equals(cellPosition) && curFocus.equals(cellPosition);
    }

    @Override
    public boolean includes(@UnknownInitialization(GridArea.class) GridArea tableDisplay)
    {
        // Rely on non-overlap of grid areas, and the way that our selection won't span multiple tables:
        return tableDisplay.contains(startAnchor); 
    }

    @Override
    public void gotoRow(Window parent)
    {
        tableSelectionLimits.gotoRow(parent, curFocus.columnIndex);
    }

    @Override
    public void notifySelected(boolean selected, boolean animateFlash)
    {
    }

    @Override
    public String toString()
    {
        // For debugging:
        return "RectTableCellSel[" + startAnchor + "->" + curFocus + "]";
    }

    @OnThread(Tag.FXPlatform)
    public static interface TableSelectionLimits
    {
        public CellPosition getTopLeftIncl();
        public CellPosition getBottomRightIncl();
        
        public void doCopy(CellPosition topLeftIncl, CellPosition bottomRightIncl);
        
        public void doDelete(CellPosition topLeftIncl, CellPosition bottomRightIncl);

        public void gotoRow(Window parent, @AbsColIndex int column);
    }
}
