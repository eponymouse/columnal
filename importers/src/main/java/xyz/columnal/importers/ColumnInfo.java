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

package xyz.columnal.importers;

import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.id.ColumnId;
import xyz.columnal.data.columntype.ColumnType;

/**
 * Created by neil on 31/10/2016.
 */
public class ColumnInfo
{
    public final ColumnType type;
    public final ColumnId title;

    public ColumnInfo(ColumnType type, ColumnId title)
    {
        this.type = type;
        this.title = title;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ColumnInfo that = (ColumnInfo) o;

        return title.equals(that.title) && type.equals(that.type);

    }

    @Override
    public int hashCode()
    {
        int result = type.hashCode();
        result = 31 * result + title.hashCode();
        return result;
    }

    @Override
    public String toString()
    {
        return "ColumnInfo{" +
            "columntype=" + type +
            ", title='" + title + '\'' +
            '}';
    }
}
