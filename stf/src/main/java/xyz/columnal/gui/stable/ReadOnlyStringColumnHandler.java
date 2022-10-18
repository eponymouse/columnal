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

import annotation.units.TableDataColIndex;
import annotation.units.TableDataRowIndex;
import javafx.scene.input.KeyCode;
import xyz.columnal.data.CellPosition;
import xyz.columnal.data.Column;
import xyz.columnal.id.ColumnId;
import xyz.columnal.gui.dtf.ReadOnlyDocument;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.function.fx.FXPlatformBiConsumer;
import xyz.columnal.utility.function.fx.FXPlatformConsumer;

public abstract class ReadOnlyStringColumnHandler implements ColumnHandler
{
    private final @TableDataColIndex int columnIndex;

    public ReadOnlyStringColumnHandler(@TableDataColIndex int columnIndex)
    {
        this.columnIndex = columnIndex;
    }

    @Override
    public void fetchValue(@TableDataRowIndex int rowIndex, FXPlatformConsumer<Boolean> focusListener, FXPlatformBiConsumer<KeyCode, CellPosition> relinquishFocus, EditorKitCallback setCellContent)
    {
        fetchValueForRow(rowIndex, s -> setCellContent.loadedValue(rowIndex, columnIndex, new ReadOnlyDocument(s)));
    }

    @OnThread(Tag.FXPlatform)
    public abstract void fetchValueForRow(int rowIndex, FXPlatformConsumer<String> withValue);

    @Override
    public void columnResized(double width)
    {
    }

    @Override
    public @OnThread(Tag.FXPlatform) void modifiedDataItems(int startRowIncl, int endRowIncl)
    {
    }

    @Override
    public @OnThread(Tag.FXPlatform) void removedAddedRows(int startRowIncl, int removedRowsCount, int addedRowsCount)
    {
    }

    @Override
    public @OnThread(Tag.FXPlatform) void addedColumn(Column newColumn)
    {
    }

    @Override
    public @OnThread(Tag.FXPlatform) void removedColumn(ColumnId oldColumnId)
    {
    }

    @Override
    public boolean isEditable()
    {
        return false;
    }
}
