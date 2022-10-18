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

import annotation.qual.Value;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.datatype.ProgressListener;;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.DataTypeValue;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.simulation.SimulationRunnable;
import xyz.columnal.utility.Utility;

import java.util.BitSet;
import java.util.List;
import java.util.stream.Stream;

/**
 * Created by neil on 08/12/2016.
 */
public class BooleanColumnStorage extends SparseErrorColumnStorage<Boolean> implements ColumnStorage<Boolean>
{
    private int length = 0;
    private final BitSet data = new BitSet();
    @OnThread(Tag.Any)
    private final DataTypeValue type;
    private final @Nullable BeforeGet<BooleanColumnStorage> beforeGet;

    public BooleanColumnStorage(@Nullable BeforeGet<BooleanColumnStorage> beforeGet, boolean isImmediateData)
    {
        super(isImmediateData);
        this.beforeGet = beforeGet;
        this.type = DataTypeValue.bool(new GetValueOrError<@Value Boolean>()
        {
            @Override
            protected @OnThread(Tag.Simulation) void _beforeGet(int index, @Nullable ProgressListener progressListener) throws InternalException, UserException
            {
                if (beforeGet != null)
                    beforeGet.beforeGet(Utility.later(BooleanColumnStorage.this), index, progressListener);
            }

            @Override
            public @Value Boolean _getWithProgress(int i, @Nullable ProgressListener progressListener) throws UserException, InternalException
            {
                return Utility.later(BooleanColumnStorage.this).getWithProgress(i, progressListener);
            }

            @Override
            public @OnThread(Tag.Simulation) void _set(int index, @Nullable @Value Boolean value) throws InternalException
            {
                data.set(index, value != null ? value : DataTypeUtility.value(false));
            }
        });
    }

    public BooleanColumnStorage(boolean isImmediateData)
    {
        this(null, isImmediateData);
    }


    private @Value Boolean getWithProgress(int i, @Nullable ProgressListener progressListener) throws UserException, InternalException
    {
        if (i < 0 || i >= filled())
            throw new UserException("Attempting to access invalid element: " + i + " of " + filled());
        return DataTypeUtility.value(data.get(i));
    }

    @Override
    public int filled(@UnknownInitialization(Object.class) BooleanColumnStorage this)
    {
        return length;
    }

    @Override
    public void addAll(Stream<Either<String, Boolean>> items) throws InternalException
    {
        for (Either<String, Boolean> item : Utility.iterableStream(items))
        {
            item.either_(e -> setError(length, e), b -> data.set(length, b));
            length += 1;
        }
    }

    @OnThread(Tag.Any)
    public DataTypeValue getType()
    {
        return type;
    }

    @Override
    public SimulationRunnable _insertRows(int index, List<@Nullable Boolean> items) throws InternalException
    {
        int count = items.size();
        // Could probably do this faster:
        // Shift up the existing bits; go downwards to avoid overlap issues:
        for (int i = length - 1; i >= index;i--)
            data.set(i + count, data.get(i));
        // Initialise bits to false:
        data.clear(index, index + count);
        for (int i = 0; i < count; i++)
        {
            int destIndex = index + i;
            Boolean item = items.get(i);
            if (item != null)
                data.set(destIndex, item);
        }
        length += count;
        return () -> _removeRows(index, count);
    }

    @Override
    public SimulationRunnable _removeRows(int index, int count) throws InternalException
    {
        // Could save some memory here:
        BitSet old = (BitSet)data.clone();
        for (int i = index; i < length - count; i++)
            data.set(i, data.get(i + count));
        length -= count;
        return () -> {
            data.clear();
            data.or(old);
            length += count;
        };
    }
}
