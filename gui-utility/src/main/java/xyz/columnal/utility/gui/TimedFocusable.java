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

package xyz.columnal.utility.gui;

import org.checkerframework.checker.nullness.qual.Nullable;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Pair;

import java.util.Arrays;
import java.util.Comparator;

public interface TimedFocusable
{
    // In terms of System.currentTimeMillis()
    @OnThread(Tag.FXPlatform)
    public long lastFocusedTime();
    
    @OnThread(Tag.FXPlatform)
    public static @Nullable TimedFocusable getRecentlyFocused(TimedFocusable... items)
    {
        long cur = System.currentTimeMillis();
        return Arrays.stream(items).map(x -> new Pair<>(x, x.lastFocusedTime())).filter(p -> p.getSecond() > cur - 250L).sorted(Comparator.comparing(p -> p.getSecond())).map(p -> p.getFirst()).findFirst().orElse(null);
    }
}
