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

import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import xyz.columnal.data.unit.Unit;

/**
 * Created by neil on 15/05/2017.
 */
public class NumberInfo
{
    private final Unit unit;

    public NumberInfo(Unit unit)
    {
        this.unit = unit;
    }

    public static final NumberInfo DEFAULT = new NumberInfo(Unit.SCALAR);
    
    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NumberInfo that = (NumberInfo) o;

        return unit.equals(that.unit);
    }

    @Override
    public int hashCode()
    {
        return unit.hashCode();
    }

    public Unit getUnit()
    {
        return unit;
    }

    public boolean sameType(@Nullable NumberInfo numberInfo)
    {
        if (numberInfo == null)
            return false;
        return unit.equals(numberInfo.unit);
    }


    public int hashCodeForType()
    {
        return unit.hashCode();
    }
}
