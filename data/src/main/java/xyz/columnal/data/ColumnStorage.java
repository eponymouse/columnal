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

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.datatype.ProgressListener;;
import xyz.columnal.data.datatype.DataTypeValue;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.simulation.SimulationRunnable;

import java.util.List;
import java.util.stream.Stream;

/**
 * Created by neil on 04/11/2016.
 */
public interface ColumnStorage<T>
{
    // The amount currently stored.  Do not assume this is all available data,
    // as many columns will be loaded/calculated on demand.
    public int filled();

    default public void add(@NonNull T item) throws InternalException
    {
        addAll(Stream.<Either<String, @NonNull T>>of(Either.<String, @NonNull T>right(item)));
    }
    public void addAll(Stream<Either<String, @NonNull T>> items) throws InternalException;

    @OnThread(Tag.Any)
    public abstract DataTypeValue getType();

    public ImmutableList<Either<String, T>> getAllCollapsed(int fromIncl, int toExcl) throws InternalException;

    // Returns revert operation
    public SimulationRunnable insertRows(int index, List<Either<String, T>> items) throws InternalException;
    // Returns revert operation
    public SimulationRunnable removeRows(int index, int count) throws InternalException;

    public static interface BeforeGet<S extends ColumnStorage<?>>
    {
        public void beforeGet(S storage, int index, @Nullable ProgressListener progressListener) throws InternalException, UserException;
    }
}
