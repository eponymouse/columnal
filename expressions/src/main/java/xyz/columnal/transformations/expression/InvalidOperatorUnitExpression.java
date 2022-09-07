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

package xyz.columnal.transformations.expression;

import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.unit.UnitManager;
import xyz.columnal.jellytype.JellyUnit;
import xyz.columnal.transformations.expression.Expression.SaveDestination;
import xyz.columnal.utility.Utility;

import java.util.stream.Collectors;

public class InvalidOperatorUnitExpression extends UnitExpression
{
    private final ImmutableList<@Recorded UnitExpression> items;

    public InvalidOperatorUnitExpression(ImmutableList<@Recorded UnitExpression> items)
    {
        this.items = items;
    }

    @Override
    public JellyUnit asUnit(@Recorded InvalidOperatorUnitExpression this, UnitManager unitManager) throws UnitLookupException
    {
        throw new UnitLookupException(null, this, ImmutableList.of());
    }

    @Override
    public String save(SaveDestination saveDestination, boolean topLevel)
    {
        if (saveDestination.needKeywords())
            return "@INVALIDOPS (" + 
                items.stream().map(item -> item.save(saveDestination, false)).collect(Collectors.joining(", "))
                + ")";
        else
            return items.stream().map(item -> item.save(saveDestination, false)).collect(Collectors.joining(""));
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        InvalidOperatorUnitExpression that = (InvalidOperatorUnitExpression) o;

        return items.equals(that.items);
    }

    @Override
    public boolean isEmpty()
    {
        return false;
    }

    @Override
    public boolean isScalar()
    {
        return false;
    }

    @Override
    public int hashCode()
    {
        return items.hashCode();
    }

    @Override
    @SuppressWarnings("recorded") // Because the replaced version is immediately loaded again
    public UnitExpression replaceSubExpression(UnitExpression toReplace, UnitExpression replaceWith)
    {
        if (this == toReplace)
            return replaceWith;
        else
            return new InvalidOperatorUnitExpression(Utility.mapListI(items, e -> e.replaceSubExpression(toReplace, replaceWith)));
    }
}
