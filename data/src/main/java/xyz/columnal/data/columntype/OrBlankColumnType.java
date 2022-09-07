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

package xyz.columnal.data.columntype;

import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.datatype.TypeManager;

/**
 * A column type which also has the option to be blank.
 */
public class OrBlankColumnType extends ColumnType
{
    // The actual column type (when non-blank)
    private final ColumnType columnType;
    private final String blankString;

    public OrBlankColumnType(ColumnType columnType, String blankString)
    {
        this.columnType = columnType;
        this.blankString = blankString;
    }

    public ColumnType getInner()
    {
        return columnType;
    }

    @Override
    public int hashCode()
    {
        return 1 + columnType.hashCode();
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        OrBlankColumnType that = (OrBlankColumnType) o;

        return columnType.equals(that.columnType);
    }

    @Override
    public String toString()
    {
        return TypeManager.MAYBE_NAME + " (" + columnType.toString() + ")";
    }

    public String getBlankString()
    {
        return blankString;
    }
}
