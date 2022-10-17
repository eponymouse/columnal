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

import xyz.columnal.id.ColumnId;
import xyz.columnal.log.Log;
import org.checkerframework.checker.nullness.qual.NonNull;
import xyz.columnal.data.ColumnStorage.BeforeGet;
import xyz.columnal.data.datatype.DataTypeValue;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.ExFunction;
import xyz.columnal.utility.function.FunctionInt;
import xyz.columnal.utility.Utility;

import java.util.stream.Stream;

/**
 * Created by neil on 14/01/2017.
 */
public class CachedCalculatedColumn<T, S extends ColumnStorage<T>> extends CalculatedColumn<S>
{
    private final S cache;
    private final ExFunction<Integer, @NonNull T> calculateItem;
    @OnThread(Tag.Any)
    private final DataTypeValue cacheType;

    public CachedCalculatedColumn(RecordSet recordSet, ColumnId name, FunctionInt<BeforeGet<S>, S> cache, ExFunction<Integer, @NonNull T> calculateItem, FunctionInt<DataTypeValue, DataTypeValue> addManualEdit) throws InternalException
    {
        super(recordSet, name);
        this.calculateItem = calculateItem;
        this.cache = cache.apply(Utility.later(this));
        this.cacheType = addManualEdit.apply(this.cache.getType());
    }

    @Override
    @OnThread(Tag.Any)
    public DataTypeValue getType() throws InternalException, UserException
    {
        return cacheType;
    }

    @Override
    protected void fillNextCacheChunk() throws InternalException
    {
        Either<String, @NonNull T> value;
        try
        {
            value = Either.right(calculateItem.apply(cache.filled()));
        }
        catch (InternalException | UserException e)
        {
            if (e instanceof InternalException)
                Log.log(e);
            value = Either.left(e.getLocalizedMessage());
        }
        
        cache.addAll(Stream.<Either<String, @NonNull T>>of(value));
    }

    @Override
    protected int getCacheFilled()
    {
        return cache.filled();
    }

    @Override
    public @OnThread(Tag.Any) AlteredState getAlteredState()
    {
        return AlteredState.OVERWRITTEN;
    }
}
