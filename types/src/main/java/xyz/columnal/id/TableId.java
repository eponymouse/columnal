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

import annotation.identifier.qual.ExpressionIdentifier;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.NonNull;
import xyz.columnal.styled.StyledShowable;
import xyz.columnal.styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * Created by neil on 14/11/2016.
 */
@OnThread(Tag.Any)
public class TableId implements Comparable<TableId>, StyledShowable
{
    private final @ExpressionIdentifier String tableId;

    public TableId(@ExpressionIdentifier String tableId)
    {
        this.tableId = tableId;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TableId tableId1 = (TableId) o;

        return tableId.equals(tableId1.tableId);

    }

    @Override
    public int hashCode()
    {
        return tableId.hashCode();
    }

    @Override
    public String toString()
    {
        return tableId;
    }

    public String getOutput()
    {
        return tableId;
    }

    public @ExpressionIdentifier String getRaw()
    {
        return tableId;
    }

    @Override
    public int compareTo(@NonNull TableId o)
    {
        return tableId.compareTo(o.tableId);
    }

    @Override
    public StyledString toStyledString()
    {
        return StyledString.s(tableId);
    }
}
