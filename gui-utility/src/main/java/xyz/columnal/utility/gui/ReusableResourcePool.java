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

import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.function.fx.FXPlatformConsumer;
import xyz.columnal.utility.function.fx.FXPlatformSupplier;

import java.util.ArrayList;

/**
 * Allows re-use of an expensive-to-construct resource, like WebView.
 */
@OnThread(Tag.FXPlatform)
public final class ReusableResourcePool<T>
{
    private final int maxPooled;
    private final ArrayList<T> pool;
    private final FXPlatformSupplier<T> makeNew;
    private final FXPlatformConsumer<T> clean;

    public ReusableResourcePool(int maxPooled, FXPlatformSupplier<T> makeNew, FXPlatformConsumer<T> clean)
    {
        this.maxPooled = maxPooled;
        this.pool = new ArrayList<>(maxPooled);
        this.makeNew = makeNew;
        this.clean = clean;
    }

    /**
     * Gets an item from the pool if available, or makes a new item
     */
    public T get()
    {
        if (pool.isEmpty())
            return makeNew.get();
        else
            return pool.remove(pool.size() - 1);
    }

    /**
     * Item will be cleaned using the clean callback
     */
    public void returnToPool(T item)
    {
        if (pool.size() < maxPooled)
        {
            clean.consume(item);
            pool.add(item);
        }
    }
}
