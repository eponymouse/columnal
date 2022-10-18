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

import annotation.qual.Value;
import annotation.units.TableDataRowIndex;
import javafx.scene.input.KeyCode;
import xyz.columnal.data.CellPosition;
import xyz.columnal.data.RecordSet.RecordSetListener;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.gui.dtf.DocumentTextField;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.function.fx.FXPlatformBiConsumer;
import xyz.columnal.utility.function.fx.FXPlatformConsumer;

import java.util.Collection;

@OnThread(Tag.FXPlatform)
public interface ColumnHandler extends RecordSetListener
{
    // Called to fetch a value.  Once available, receiver should be called.
    // Until then it will be blank.  You can call receiver multiple times though,
    // so you can just call it with a placeholder before returning.
    public void fetchValue(@TableDataRowIndex int rowIndex, FXPlatformConsumer<Boolean> focusListener, FXPlatformBiConsumer<KeyCode, CellPosition> relinquishFocus, EditorKitCallback setCellContent);

    // Called when the column gets resized (graphically).  Width is in pixels
    public void columnResized(double width);

    // Should return an InputMap, if any, to put on the parent node of the display.
    // Useful if you want to be able to press keys directly without beginning editing
    //public @Nullable InputMap<?> getInputMapForParent(int rowIndex);

    // Called when the user initiates an error, either by double-clicking
    // (in which case the point is passed) or by pressing enter (in which case
    // point is null).
    // Will only be called if isEditable returns true
    //public void edit(int rowIndex, @Nullable Point2D scenePoint);

    // Can this column be edited?
    public boolean isEditable();

    // Is this column value currently being edited?
    //public boolean editHasFocus(int rowIndex);
    
    // Used for doing a copy-to-clipboard of table content:
    @OnThread(Tag.Simulation)
    public @Value Object getValue(int index) throws InternalException, UserException;

    void styleTogether(Collection<? extends DocumentTextField> cellsInColumn, double columnSize);
}
