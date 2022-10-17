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

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Pair;

public interface ProgressListener
{
    @OnThread(Tag.Simulation)
    public void progressUpdate(double progress);

    public static Pair<@Nullable ProgressListener, @Nullable ProgressListener> split(final @Nullable ProgressListener prog)
    {
        if (prog == null)
            return new Pair<@Nullable ProgressListener, @Nullable ProgressListener>(null, null);
        @NonNull ProgressListener progFinal = prog;
        return new Pair<>(a -> progFinal.progressUpdate(a * 0.5), b -> progFinal.progressUpdate(b * 0.5 + 0.5));
    }
}
