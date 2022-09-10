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

package xyz.columnal.data.datatype;

import annotation.identifier.qual.ExpressionIdentifier;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.Nullable;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * Created by neil on 14/11/2016.
 */
@OnThread(Tag.Any)
public class TypeId implements Comparable<TypeId>
{
    private final @ExpressionIdentifier String typeId;

    public TypeId(@ExpressionIdentifier String typeId)
    {
        this.typeId = typeId;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TypeId tableId1 = (TypeId) o;

        return typeId.equals(tableId1.typeId);

    }

    @Override
    public int hashCode()
    {
        return typeId.hashCode();
    }

    @Override
    public String toString()
    {
        return typeId;
    }

    public String getOutput()
    {
        return typeId;
    }

    @SuppressWarnings("i18n")
    public @Localized @ExpressionIdentifier String getRaw()
    {
        return typeId;
    }

    @Override
    public int compareTo(TypeId o)
    {
        return typeId.compareTo(o.typeId);
    }
}
