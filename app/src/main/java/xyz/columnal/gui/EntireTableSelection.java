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

package xyz.columnal.gui;

import annotation.units.AbsColIndex;
import javafx.stage.Window;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.CellPosition;
import xyz.columnal.gui.grid.CellSelection;
import xyz.columnal.gui.grid.GridArea;
import xyz.columnal.gui.grid.RectangleBounds;
import xyz.columnal.gui.table.HeadedDisplay;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;

public class EntireTableSelection implements CellSelection
{
    private final HeadedDisplay selected;
    // Although we select the whole table, if they move out up/down, we stay
    // in the column they entered from:
    private final @AbsColIndex int column;

    public EntireTableSelection(HeadedDisplay selected, @AbsColIndex int column)
    {
        this.selected = selected;
        this.column = column;
    }

    @Override
    public void doCopy()
    {
        selected.doCopy(null);
    }

    @Override
    public void doPaste()
    {
        // TODO
    }

    @Override
    public void doDelete()
    {
        selected.doDelete();
    }

    @Override
    public CellPosition getActivateTarget()
    {
        return selected.getPosition();
    }

    @Override
    public CellSelection atHome(boolean extendSelection)
    {
        return this;
    }

    @Override
    public CellSelection atEnd(boolean extendSelection)
    {
        return this;
    }

    @Override
    public Either<CellPosition, CellSelection> move(boolean extendSelection, int byRows, int byColumns)
    {
        if (!extendSelection)
        {
            if (byRows != 0)
            {
                // Conceieve of this as going up/down first to a single cell, then across:
                return Either.left(selected.getPosition().offsetByRowCols(byRows, (column - selected.getPosition().columnIndex) + byColumns));
            }
            else
            {
                // Only going left/right, just move out of the end of the table:
                if (byColumns <= 0)
                    return Either.left(selected.getPosition().offsetByRowCols(byRows, byColumns));
                else
                    return Either.left(selected.getPosition().offsetByRowCols(byRows, byColumns + selected.getBottomRightIncl().columnIndex - selected.getPosition().columnIndex));
            }
        }
        return Either.right(this);
    }

    @Override
    public CellPosition positionToEnsureInView()
    {
        return selected.getPosition();
    }

    @Override
    public RectangleBounds getSelectionDisplayRectangle()
    {
        return new RectangleBounds(selected.getPosition(), selected.getBottomRightIncl());
    }

    @Override
    public boolean isExactly(CellPosition cellPosition)
    {
        // Whole table, so won't be single cell:
        return false;
    }

    @Override
    public boolean includes(@UnknownInitialization(GridArea.class) GridArea tableDisplay)
    {
        return this.selected == tableDisplay;
    }

    @Override
    public void gotoRow(Window parent)
    {
        selected.gotoRow(parent, column);
    }

    @Override
    public void notifySelected(boolean selected, boolean animateFlash)
    {
        if (selected && animateFlash)
            this.selected.flashHeader();
    }

    @Override
    public @Nullable CellSelection extendTo(CellPosition cellPosition)
    {
        return null;
    }

    // For debugging
    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public String toString()
    {
        return "EntireTableSelection[" + selected.getPosition() + "-" + selected.getBottomRightIncl() + "]";
    }
}
