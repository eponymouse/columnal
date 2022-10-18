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
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.datatype.ProgressListener;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.DataTypeValue;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.simulation.SimulationRunnable;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Utility.ListEx;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * For some reason, thi is the difficult one to wrap your head around.  An array
 * is a vector of items, which could be a vector of strings, a vector of tagged items,
 * or a vector of arrays.  Several crucial facts:
 *
 *  - Since this is a column storage, each item is one row, which is itself an array.
 *  - This storage only stores the head (in Haskell terms)
 *    of the item, not the full item, which might be cached elsewhere (e.g. if this storage
 *    is the result of a sort, no point duplicating the array).
 *
 * Thus the storage is just a list of DataTypeValue, i.e. accessors of the array content.
 */
public class ArrayColumnStorage extends SparseErrorColumnStorage<ListEx> implements ColumnStorage<ListEx>
{
    // We are a column storage.  Each element here is one row, which is also
    // a list because it is an array.  We are a list of lists (column of arrays)
    private final ArrayList<@Value ListEx> storage = new ArrayList<>();
    @OnThread(Tag.Any)
    private final DataTypeValue type;

    // Constructor for array version
    public ArrayColumnStorage(DataType innerToCopy, @Nullable BeforeGet<ArrayColumnStorage> beforeGet, boolean isImmediateData) throws InternalException
    {
        super(isImmediateData);
        DataType innerFinal = innerToCopy;
        this.type = DataTypeValue.array(innerToCopy, new GetValueOrError<@Value ListEx>()
        {
            @Override
            public @Value ListEx _getWithProgress(int i, @Nullable ProgressListener prog) throws UserException, InternalException
            {
                return storage.get(i);
            }

            @Override
            public void _beforeGet(int i, @Nullable ProgressListener prog) throws InternalException, UserException
            {
                if (beforeGet != null)
                    beforeGet.beforeGet(Utility.later(ArrayColumnStorage.this), i, prog);
            }

            @Override
            public void _set(int index, @Nullable @Value ListEx v) throws InternalException, UserException
            {
                storage.set(index, v == null ? ListEx.empty() : v);
            }
        });
    }

    @Override
    public int filled()
    {
        return storage.size();
    }
/*
    public List<Object> get(int index) throws InternalException, UserException
    {
        return (List)storage.get(index).getFullList(type.getArrayLength(index));
    }*/

    @Override
    public void addAll(Stream<Either<String, ListEx>> items) throws InternalException
    {
        for (Either<String, ListEx> item : Utility.iterableStream(items))
        {
            storage.add(DataTypeUtility.value(item.<ListEx>either(err -> {
                setError(storage.size(), err);
                return ListEx.empty();
            }, l -> l)));
        }
    }

    @Override
    @OnThread(Tag.Any)
    public DataTypeValue getType()
    {
        return type;
    }

    @Override
    public SimulationRunnable _insertRows(int index, List<@Nullable ListEx> items) throws InternalException
    {
        storage.ensureCapacity(storage.size() + items.size());
        int curIndex = index;
        for (ListEx item : items)
        {
            if (item == null)
                item = ListEx.empty();
            storage.add(curIndex, DataTypeUtility.value(item));
            curIndex += 1;
        }
        int count = items.size();
        return () -> _removeRows(index, count);
    }

    @Override
    public SimulationRunnable _removeRows(int index, int count) throws InternalException
    {
        List<@Value ListEx> old = new ArrayList<>(storage.subList(index, index + count));
        storage.subList(index, index + count).clear();
        return () -> storage.addAll(index, old);
    }
}
