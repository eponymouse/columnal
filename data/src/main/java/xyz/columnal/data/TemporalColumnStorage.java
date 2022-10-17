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
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.datatype.ProgressListener;;
import xyz.columnal.data.datatype.DataType.DateTimeInfo;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.DataTypeValue;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.DumbObjectPool;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.simulation.SimulationRunnable;
import xyz.columnal.utility.Utility;

import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Created by neil on 04/11/2016.
 */
public class TemporalColumnStorage extends SparseErrorColumnStorage<TemporalAccessor> implements ColumnStorage<TemporalAccessor>
{
    private final ArrayList<@Value TemporalAccessor> values;
    private final DumbObjectPool<@Value TemporalAccessor> pool;
    @OnThread(Tag.Any)
    private final DateTimeInfo dateTimeInfo;
    
    @OnThread(value = Tag.Any,requireSynchronized = true)
    private @MonotonicNonNull DataTypeValue dataType;
    private final @Nullable BeforeGet<TemporalColumnStorage> beforeGet;

    public TemporalColumnStorage(DateTimeInfo dateTimeInfo, boolean isImmediateData) throws InternalException
    {
        this(dateTimeInfo, null, isImmediateData);
    }

    public TemporalColumnStorage(DateTimeInfo dateTimeInfo, @Nullable BeforeGet<TemporalColumnStorage> beforeGet, boolean isImmediateData) throws InternalException
    {
        super(isImmediateData);
        this.values = new ArrayList<>();
        this.pool = new DumbObjectPool<>((Class<@Value TemporalAccessor>)TemporalAccessor.class, 1000, (Comparator<@Value TemporalAccessor>)dateTimeInfo.getComparator(true));
        this.dateTimeInfo = dateTimeInfo;
        this.beforeGet = beforeGet;
    }

    @Override
    public int filled()
    {
        return values.size();
    }

    private @Value TemporalAccessor get(int index, @Nullable ProgressListener progressListener) throws InternalException, UserException
    {
        if (index < 0 || index >= filled())
            throw new UserException("Attempting to access invalid element: " + index + " of " + filled());
        return values.get(index);
    }

    @Override
    public void addAll(Stream<Either<String, TemporalAccessor>> items) throws InternalException
    {
        for (Either<String, TemporalAccessor> item : Utility.iterableStream(items))
        {
            TemporalAccessor t = item.eitherInt(err -> {
                setError(values.size(), err);
                return dateTimeInfo.getDefaultValue();
            }, v -> v);
            @Value TemporalAccessor value = DataTypeUtility.value(dateTimeInfo, t);
            if (value == null)
            {
                setError(values.size(), t.toString());
                value = dateTimeInfo.getDefaultValue();
            }
            this.values.add(pool.pool(value));
        }
    }

    @OnThread(Tag.Any)
    public synchronized DataTypeValue getType()
    {
        /*
        if (longs != null)
        {
            for (long l : longs)
                if (l == SEE_BIGDEC)
                    return v -> v.number();
        }
        */
        if (dataType == null)
        {
            dataType = DataTypeValue.date(dateTimeInfo, new GetValueOrError<@Value TemporalAccessor>()
            {
                @Override
                protected @OnThread(Tag.Simulation) void _beforeGet(int index, @Nullable ProgressListener progressListener) throws InternalException, UserException
                {
                    if (beforeGet != null)
                        beforeGet.beforeGet(TemporalColumnStorage.this, index, progressListener);
                }

                @Override
                public @Value TemporalAccessor _getWithProgress(int i, @Nullable ProgressListener prog) throws UserException, InternalException
                {
                    return TemporalColumnStorage.this.get(i, prog);
                }

                @Override
                public void _set(int index, @Nullable @Value TemporalAccessor value) throws InternalException, UserException
                {
                    if (value != null)
                        values.set(index, value);
                }
            });
        }
        return dataType;
    }

    @Override
    public SimulationRunnable _insertRows(int index, List<@Nullable TemporalAccessor> items) throws InternalException
    {
        if (index < 0 || index > values.size())
            throw new InternalException("Trying to insert rows at invalid index: " + index + " length is: " + values.size());
        values.ensureCapacity(values.size() + items.size());
        int curIndex = index;
        for (@Nullable TemporalAccessor item : items)
        {
            @Value TemporalAccessor itemValue = item == null ? null : DataTypeUtility.value(dateTimeInfo, item);
            if (itemValue == null)
            {
                @Value TemporalAccessor dummy = dateTimeInfo.getDefaultValue();
                values.add(curIndex, pool.pool(dummy));
            }
            else
                values.add(curIndex, pool.pool(itemValue));
            
            curIndex += 1;
        }
        int count = items.size();
        return () -> _removeRows(index, count);
    }

    @Override
    public SimulationRunnable _removeRows(int index, int count) throws InternalException
    {
        if (index < 0 || index > values.size())
            throw new InternalException("Trying to remove rows at invalid index: " + index + " length is: " + values.size());
        List<@Value TemporalAccessor> old = new ArrayList<>(values.subList(index, index + count));
        values.subList(index, index + count).clear();
        return () -> values.addAll(index, old);
    }
}
