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

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.utility.adt.Either;

import java.util.Objects;

public class BoolColumnType extends ColumnType
{
    private final String lowerCaseTrue;
    private final String lowerCaseFalse;

    public BoolColumnType(String lowerCaseTrue, String lowerCaseFalse)
    {
        this.lowerCaseTrue = lowerCaseTrue;
        this.lowerCaseFalse = lowerCaseFalse;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BoolColumnType that = (BoolColumnType) o;
        return Objects.equals(lowerCaseTrue, that.lowerCaseTrue);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(lowerCaseTrue);
    }

    @Override
    public String toString()
    {
        return "Boolean[" + lowerCaseTrue + "]";
    }

    public Either<String, Boolean> parse(@NonNull String s)
    {
        if (s.trim().equalsIgnoreCase(lowerCaseTrue))
            return Either.right(Boolean.TRUE);
        else if (s.trim().equalsIgnoreCase(lowerCaseFalse))
            return Either.right(Boolean.FALSE);
        else
            return Either.left(s);
    }
}
