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

import annotation.qual.Value;
import annotation.units.TableDataRowIndex;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.datatype.DataType;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.function.Function;

/**
 * Created by neil on 29/05/2017.
 */
public class TableOperations
{
    public final @Nullable RenameTable renameTable;
    public final Function<ColumnId, @Nullable DeleteColumn> deleteColumn;
    public final @Nullable AppendRows appendRows;
    // Row index to insert at, count
    public final @Nullable InsertRows insertRows;
    // Row index to delete at (incl), count
    public final @Nullable DeleteRows deleteRows;

    @OnThread(Tag.Any)
    public TableOperations(@Nullable RenameTable renameTable, Function<ColumnId, @Nullable DeleteColumn> deleteColumn, @Nullable AppendRows appendRows, @Nullable InsertRows insertRows, @Nullable DeleteRows deleteRows)
    {
        this.renameTable = renameTable;
        this.deleteColumn = deleteColumn;
        this.appendRows = appendRows;
        this.insertRows = insertRows;
        this.deleteRows = deleteRows;
    }

    // Delete column (only available if this is source of the column)
    @FunctionalInterface
    public static interface DeleteColumn
    {
        @OnThread(Tag.Simulation)
        public void deleteColumn(ColumnId columnId);
    }

    // Add rows at end
    @FunctionalInterface
    public static interface AppendRows
    {
        @OnThread(Tag.Simulation)
        public void appendRows(int count);
    }

    // Add rows in middle
    public static interface InsertRows
    {
        @OnThread(Tag.Simulation)
        public void insertRows(@TableDataRowIndex int beforeRowIndex, int count);

        // TODO
        //@OnThread(Tag.Simulation)
        //public void pasteRows(int rowIndex, List<Map<ColumnId, String>> content);
    }

    public static interface DeleteRows
    {
        @OnThread(Tag.Simulation)
        public void deleteRows(@TableDataRowIndex int rowIndex, int count);
    }
    
    public static interface RenameTable
    {
        @OnThread(Tag.Simulation)
        public void renameTable(TableId newName);
    }
}
