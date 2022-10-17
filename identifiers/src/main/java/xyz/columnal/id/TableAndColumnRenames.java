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
package xyz.columnal.id;

import com.google.common.collect.ImmutableMap;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Pair;

import java.util.HashMap;
import java.util.Map;

/**
 * A class keeping track of any pending table and/or column renames.  Column renames
 * are stored with a reference to the table that they originate from.
 * 
 * Note that this class is mutable.  We save tables in dependency order, and let's imagine that we have:
 * A <- Sort <- ManualEdit <- Calculate <- Filter
 * 
 * If you rename a column in A, the same column should be renamed in Sort
 * -- but it should also be renamed in the ManualEdit, and in the
 * Calculate and the Filter.  So each table, as it queries for its own
 * renames, passes the table id which defined the column, and this is
 * used to propagate renames to later tables, one step at a time.
 */
@OnThread(Tag.Any)
public class TableAndColumnRenames
{
    // Maps an old table id to a pair of:
    //  - possible new table id (null if unchanged)
    //  - map from old column ids to new column ids for that table
    //    if a column id is not present, it means it is unaffected.
    private final HashMap<TableId, Pair<@Nullable TableId, HashMap<ColumnId, ColumnId>>> renames;
    private final @Nullable TableId defaultTableId;

    private <COLMAP extends @NonNull Map<ColumnId, ColumnId>> TableAndColumnRenames(@Nullable TableId defaultTableId, Map<TableId, Pair<@Nullable TableId, @NonNull COLMAP>> renames)
    {
        this.defaultTableId = defaultTableId;
        this.renames = new HashMap<>();
        renames.forEach((t, p) -> {
            this.renames.put(t, p.mapSecond(m -> new HashMap<>(m)));
        });
    }
    
    public TableAndColumnRenames(ImmutableMap<TableId, Pair<@Nullable TableId, ImmutableMap<ColumnId, ColumnId>>> renames)
    {
        this(null, renames);
    }

    public TableId tableId(TableId tableId)
    {
        Pair<@Nullable TableId, HashMap<ColumnId, ColumnId>> info = renames.get(tableId);
        if (info != null && info.getFirst() != null)
            return info.getFirst();
        else
            return tableId;
    }

    // Note: pass the OLD TableId, not the new one.  If you pass null, the default is used (if set)
    public Pair<@Nullable TableId, ColumnId> columnId(@Nullable TableId oldTableId, ColumnId columnId, @Nullable TableId columnNameOriginatesFromTableId)
    {
        final @NonNull TableId tableId;
        // If no table id known, no way to locate:
        if (oldTableId == null)
        {
            if (defaultTableId == null)
                return new Pair<>(null, columnId);
            else
                tableId = defaultTableId;
        }
        else
            tableId = oldTableId;
        
        @Nullable Pair<@Nullable TableId, HashMap<ColumnId, ColumnId>> info = renames.get(tableId);

        Pair<@Nullable TableId, ColumnId> renamed;
        if (info != null)
        {
            renamed = info.<@Nullable TableId, ColumnId>map(t -> {
                if (oldTableId == null && t != null && t.equals(defaultTableId))
                    return oldTableId;
                else
                    return t;
            }, c -> c.getOrDefault(columnId, columnId));
        }
        else
            renamed = new Pair<>(oldTableId, columnId);

        if (columnNameOriginatesFromTableId != null && renamed.getSecond().equals(columnId))
        {
            // If it is not renamed in our table, check source table:
            // (Passing null as third param here prevents further recursion)
            ColumnId renamedInSource = columnId(columnNameOriginatesFromTableId, columnId, null).getSecond();
            if (!renamedInSource.equals(columnId))
            {
                // Propagate:
                if (info == null)
                {
                    info = new Pair<>(null, new HashMap<>());
                    renames.put(tableId, info);
                }
                info.getSecond().put(columnId, renamedInSource);
                renamed = renamed.mapSecond(_c -> renamedInSource);
            }
        }
        return renamed;
    }
    
    public static final TableAndColumnRenames EMPTY = new TableAndColumnRenames(ImmutableMap.of());

    // Note that this is copy operation, so any table registration
    // (of which columns are derived from which source table) is not stored in parent.
    // This is okay as it stands, because this method is only used
    // for passing column renames to expressions.
    public TableAndColumnRenames withDefaultTableId(TableId tableId)
    {
        return new TableAndColumnRenames(tableId, ImmutableMap.<TableId, Pair<@Nullable TableId, @NonNull HashMap<ColumnId, ColumnId>>>copyOf(renames));
    }

    // Are we renaming the given table, or any of its columns?
    public boolean isRenamingTableId(TableId tableId)
    {
        Pair<@Nullable TableId, HashMap<ColumnId, ColumnId>> details = renames.get(tableId);
        return details != null && (details.getFirst() != null || !details.getSecond().isEmpty());
    }

    // Copies all renames forward from srcTableId to id
    public void useColumnsFromTo(TableId srcTableId, TableId id)
    {
        renames.computeIfAbsent(id, t -> new Pair<@Nullable TableId, HashMap<ColumnId, ColumnId>>(null, new HashMap<>())).getSecond().putAll(renames.getOrDefault(srcTableId, new Pair<>(null, new HashMap<>())).getSecond());
    }
}
