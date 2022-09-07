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

import org.checkerframework.checker.nullness.qual.Nullable;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.io.File;
import java.util.Objects;

@OnThread(Tag.Any)
public class Settings
{
    // IMPORTANT: if you add a field, you must redefine equals and hash code!
    
    // Can be null, in which case use PATH  (effectively just "R", but appears blank in settings)
    public final @Nullable File pathToRExecutable;
    public final boolean useColumnalRLibs;

    public Settings(@Nullable File pathToRExecutable, boolean useColumnalRLibs)
    {
        this.pathToRExecutable = pathToRExecutable;
        this.useColumnalRLibs = useColumnalRLibs;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Settings settings = (Settings) o;
        return useColumnalRLibs == settings.useColumnalRLibs &&
            Objects.equals(pathToRExecutable, settings.pathToRExecutable);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(pathToRExecutable, useColumnalRLibs);
    }
}
