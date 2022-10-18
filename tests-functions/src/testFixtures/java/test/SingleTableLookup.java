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

package test;

import com.google.common.collect.ImmutableMap;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.Column;
import xyz.columnal.data.RecordSet;
import xyz.columnal.data.datatype.DataTypeValue;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.id.ColumnId;
import xyz.columnal.id.TableId;
import xyz.columnal.log.Log;
import xyz.columnal.transformations.expression.Expression;
import xyz.columnal.transformations.expression.Expression.ColumnLookup;
import xyz.columnal.utility.adt.Pair;

import java.util.stream.Stream;

public class SingleTableLookup implements ColumnLookup
{
    private final TableId tableId;
    private final RecordSet srcTable;

    public SingleTableLookup(TableId tableId, RecordSet srcTable)
    {
        this.tableId = tableId;
        this.srcTable = srcTable;
    }

    @Override
    public Stream<Pair<@Nullable TableId, ColumnId>> getAvailableColumnReferences()
    {
        return srcTable.getColumns().stream().map(c -> new Pair<>(null, c.getName()));
    }

    @Override
    public Stream<TableId> getAvailableTableReferences()
    {
        return Stream.of(tableId);
    }

    @Override
    public Stream<ClickedReference> getPossibleColumnReferences(TableId tableId, ColumnId columnId)
    {
        return Stream.empty(); // Not used in testing
    }

    @Override
    public @Nullable FoundColumn getColumn(Expression expression, @Nullable TableId refTableId, ColumnId refColumnId)
    {
        try
        {
            if (refTableId == null || refTableId.equals(tableId))
            {
                Column column = srcTable.getColumnOrNull(refColumnId);
                if (column != null)
                    return new FoundColumn(tableId, true, column.getType(), null);
            }
        } catch (InternalException | UserException e)
        {
            Log.log(e);
        }
        return null;
    }

    @Override
    public @Nullable FoundTable getTable(@Nullable TableId tableName) throws UserException, InternalException
    {
        if (!tableId.equals(tableName))
            return null;

        return new FoundTable()
        {
            @Override
            public TableId getTableId()
            {
                return tableId;
            }

            @Override
            public ImmutableMap<ColumnId, DataTypeValue> getColumnTypes() throws InternalException, UserException
            {
                ImmutableMap.Builder<ColumnId, DataTypeValue> columns = ImmutableMap.builder();
                for (Column column : srcTable.getColumns())
                {
                    columns.put(column.getName(), column.getType());
                }
                return columns.build();
            }

            @Override
            public int getRowCount() throws InternalException, UserException
            {
                return srcTable.getLength();
            }
        };
    }
}
