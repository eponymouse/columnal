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

package xyz.columnal.data.unit;

import annotation.identifier.qual.UnitIdentifier;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

public class SingleUnit implements Comparable<SingleUnit>
{
    private final @UnitIdentifier String unitName;
    private final String description;
    private final String prefix;
    private final String suffix;

    public SingleUnit(@UnitIdentifier String unitName, String description, String prefix, String suffix)
    {
        this.unitName = unitName;
        this.description = description;
        this.prefix = prefix;
        this.suffix = suffix;
    }

    public @UnitIdentifier String getName()
    {
        return unitName;
    }

    public String getPrefix()
    {
        return prefix;
    }

    public String getSuffix()
    {
        return suffix;
    }

    public String getDescription()
    {
        return description;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SingleUnit that = (SingleUnit) o;

        return unitName.equals(that.unitName);
    }

    @Override
    public int hashCode()
    {
        return unitName.hashCode();
    }

    @Override
    public String toString()
    {
        return unitName;
    }
    
    @Override
    public int compareTo(@NonNull SingleUnit o)
    {
        return unitName.compareTo(o.unitName);
    }
}
