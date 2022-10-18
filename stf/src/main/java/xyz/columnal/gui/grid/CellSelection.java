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

import javafx.stage.Window;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.CellPosition;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;

/**
 * A selection of cells.  This might be, for example:
 * - A selection of one or more rows
 * - A selection of one or more columns
 * - The entire table
 * - A rectangular grid of cells within the table of at least 1x1
 */
@OnThread(Tag.FXPlatform)
public interface CellSelection
{
    // Copy the value of the selection to the clipboard, if that operation makes sense.
    public void doCopy();

    // Paste the value of the clipboard, if that operation makes sense.
    public void doPaste();

    // Perform a delete, if that operation makes sense.
    public void doDelete();

    // Which position should we target if they try to activate?
    CellPosition getActivateTarget();

    // Extend the current selection to the given cell, return null if not possible
    @Nullable CellSelection extendTo(CellPosition cellPosition);

    /**
     * Gets a new selection that is the result of pressing home on this one.
     */
    public CellSelection atHome(boolean extendSelection);

    /**
     * Gets a new selection that is the result of pressing end on this one.
     */
    public CellSelection atEnd(boolean extendSelection);

    /**
     * Move the selection by that number of rows and columns.  If this would result in moving outside
     * the current selection, the new position is returned in Either.left.  Otherwise, a new selection
     * is returned in Either.right
     */
    public Either<CellPosition, CellSelection> move(boolean extendSelection, int byRows, int byColumns);

    /**
     * What cell should we scroll to, to ensure that the focus part of the selection is visible?
     */
    public CellPosition positionToEnsureInView();

    /**
     * What are the display bounds of this rectangle for drawing on screen?  Only used for drawing.
     */
    public RectangleBounds getSelectionDisplayRectangle();

    // Is the current selection the single cell supplied and nothing more?
    public boolean isExactly(CellPosition cellPosition);

    /**
     * Does the selection include the given grid area?  Can either be worked out by
     * looking at physical area, or by casting and checking against a known table.
     */
    public boolean includes(@UnknownInitialization(GridArea.class) GridArea tableDisplay);
    
    public void gotoRow(Window parent);
    
    // Called when selected or deselected
    public void notifySelected(boolean selected, boolean animateFlash);
}
