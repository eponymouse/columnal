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

package xyz.columnal.transformations;

import com.google.common.collect.ImmutableMap;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.data.Column;
import xyz.columnal.data.Table;
import xyz.columnal.data.datatype.DataTypeValue;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.id.ColumnId;
import xyz.columnal.id.TableId;
import xyz.columnal.transformations.expression.Expression.ColumnLookup.FoundTable;

// Default implementation of FoundTable
public final class FoundTableActual implements FoundTable
{
    private final Table table;

    public FoundTableActual(Table table)
    {
        this.table = table;
    }

    @Override
    public TableId getTableId()
    {
        return table.getId();
    }

    @Override
    public ImmutableMap<ColumnId, DataTypeValue> getColumnTypes() throws InternalException, UserException
    {
        ImmutableMap.Builder<ColumnId, DataTypeValue> columns = ImmutableMap.builder();
        for (Column column : table.getData().getColumns())
        {
            columns.put(column.getName(), column.getType());
        }
        return columns.build();
    }

    @OnThread(Tag.Simulation)
    @Override
    public int getRowCount() throws InternalException, UserException
    {
        return table.getData().getLength();
    }
}
