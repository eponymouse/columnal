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

import annotation.identifier.qual.ExpressionIdentifier;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.styled.StyledShowable;
import xyz.columnal.styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Utility;

import java.io.Serializable;

/**
 * Created by neil on 14/11/2016.
 *
 * Serializable for interface reasons, for not saving to file
 */
@OnThread(Tag.Any)
public class ColumnId implements Comparable<ColumnId>, Serializable, StyledShowable
{
    private static final long serialVersionUID = -6813720608766860501L;
    private final @ExpressionIdentifier String columnId;

    public ColumnId(@ExpressionIdentifier String columnId)
    {
        this.columnId = columnId;
    }

    public static boolean validCharacter(int codePoint, boolean start)
    {
        return Character.isLetter(codePoint) || ((codePoint == ' ' || Character.isDigit(codePoint)) && !start);
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ColumnId tableId1 = (ColumnId) o;

        return columnId.equals(tableId1.columnId);

    }

    @Override
    public int hashCode()
    {
        return columnId.hashCode();
    }

    @Override
    public String toString()
    {
        return columnId;
    }

    public String getOutput()
    {
        return columnId;
    }

    @SuppressWarnings("i18n")
    public @Localized @ExpressionIdentifier String getRaw()
    {
        return columnId;
    }

    @Override
    public int compareTo(ColumnId o)
    {
        return columnId.compareTo(o.columnId);
    }

    @Override
    public StyledString toStyledString()
    {
        return StyledString.s(columnId);
    }
}
