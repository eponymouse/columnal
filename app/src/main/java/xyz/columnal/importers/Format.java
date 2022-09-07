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

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.importers.GuessFormat.TrimChoice;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Created by neil on 31/10/2016.
 */
public class Format
{
    public final TrimChoice trimChoice;
    public final ImmutableList<ColumnInfo> columnTypes;
    public final List<String> problems = new ArrayList<>();

    public Format(TrimChoice trimChoice, ImmutableList<ColumnInfo> columnTypes)
    {
        this.trimChoice = trimChoice;
        this.columnTypes = columnTypes;
    }

    public Format(Format copyFrom)
    {
        this(copyFrom.trimChoice, copyFrom.columnTypes);
    }

    public void recordProblem(String problem)
    {
        problems.add(problem);
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Format format = (Format) o;
        return Objects.equals(trimChoice, format.trimChoice) &&
                Objects.equals(columnTypes, format.columnTypes);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(trimChoice, columnTypes);
    }

    @Override
    public String toString()
    {
        return "Format{" +
            ", columnTypes=" + columnTypes +
            '}';
    }
}
