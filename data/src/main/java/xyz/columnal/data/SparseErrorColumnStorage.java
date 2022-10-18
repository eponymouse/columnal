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
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.datatype.ProgressListener;;
import xyz.columnal.data.datatype.DataTypeValue.GetValue;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.InvalidImmediateValueException;
import xyz.columnal.error.UserException;
import xyz.columnal.styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.simulation.SimulationRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Function;

public abstract class SparseErrorColumnStorage<T> implements ColumnStorage<T>
{
    // Is this an original data source (true), or storage for
    // calculated values (false)
    private final boolean isImmediateData;
    private HashMap<Integer, String> errorEntries = new HashMap<>();
    // Max of errorEntries.keySet() or -1 if empty
    private int latestError = -1;

    protected SparseErrorColumnStorage(boolean isImmediateData)
    {
        this.isImmediateData = isImmediateData;
    }

    protected final @Nullable String getError(int row)
    {
        return errorEntries.get(row);
    }
    
    protected final void setError(@UnknownInitialization(SparseErrorColumnStorage.class) SparseErrorColumnStorage<T> this, int row, String error)
    {
        errorEntries.put(row, error);
        if (row > latestError)
            latestError = row;
    }

    private void recalculateLatestError(@UnknownInitialization(SparseErrorColumnStorage.class) SparseErrorColumnStorage<T> this)
    {
        latestError = errorEntries.keySet().stream().mapToInt(i -> i).max().orElse(-1);
    }

    protected final void unsetError(@UnknownInitialization(SparseErrorColumnStorage.class) SparseErrorColumnStorage<T> this, int row)
    {
        errorEntries.remove(row);
        if (row >= latestError)
            recalculateLatestError();
    }
    
    private final HashMap<Integer, String> mapErrors(Function<Integer, @Nullable Integer> rowChange)
    {
        // Need to be careful here not to overwrite keys which are swapping:
        HashMap<Integer, String> modified = new HashMap<>();
        HashMap<Integer, String> removed = new HashMap<>();
        errorEntries.forEach((k, v) -> {
            @Nullable Integer newKey = rowChange.apply(k);
            if (newKey != null)
                modified.put(newKey, v);
            else
                removed.put(k, v);
        });
        errorEntries = modified;
        recalculateLatestError();
        return removed;
    }

    public final ImmutableList<Either<String, T>> getAllCollapsed(int fromIncl, int toExcl) throws InternalException
    {
        ImmutableList.Builder<Either<String, T>> r = ImmutableList.builderWithExpectedSize(toExcl - fromIncl);
        
        try
        {
            for (int i = fromIncl; i < toExcl; i++)
            {
                String error = getError(i);
                if (error != null)
                    r.add(Either.left(error));
                else
                {
                    @SuppressWarnings("unchecked")
                    T collapsed = (T) getType().getCollapsed(i);
                    r.add(Either.right(collapsed));
                }

            }
        }
        catch (UserException e)
        {
            throw new InternalException("Unexpected user exception loading from column storage", e);
        }
        return r.build();
    }

    // Insert, but without doing errors at all, other than to add blanks where there is null
    protected abstract SimulationRunnable _insertRows(int index, List<@Nullable T> items) throws InternalException;
    

    // Remove, but without doing errors at all
    protected abstract SimulationRunnable _removeRows(int index, int count) throws InternalException;

    // Returns revert operation
    @Override
    public final SimulationRunnable insertRows(int index, List<Either<String, T>> itemsErr) throws InternalException
    {
        int itemsSize = itemsErr.size();
        if (index <= latestError)
            mapErrors(i -> i < index ? i : i + itemsSize);
        
        ArrayList<@Nullable T> items = new ArrayList<>();
        for (int i = 0; i < itemsErr.size(); i++)
        {
            Either<String, T> errOrValue = itemsErr.get(i);
            int iFinal = i;
            items.add(errOrValue.<@Nullable T>either(err -> {
                setError(index + iFinal, err);
                return null;
            }, v -> {
                return v;
            }));
        }
        
        SimulationRunnable revert = _insertRows(index, items);
        return () -> {
            revert.run();
            mapErrors(i -> i < index ? i : (i >= index + itemsSize ? i - itemsSize : null));
        };
    }
    
    // Returns revert operation
    @Override
    public final SimulationRunnable removeRows(int index, int count) throws InternalException
    {
        SimulationRunnable revert = _removeRows(index, count);
        HashMap<Integer, String> removed = mapErrors(i -> i < index ? i : (i < index + count ? null : i - count));
        return () -> {
            mapErrors(i -> i < index ? i : i + count);
            removed.forEach(this::setError);
            revert.run();
        };
    }
    
    protected abstract class GetValueOrError<V extends @Value @NonNull Object> implements GetValue<@Value V>
    {
        @OnThread(Tag.Any)
        public GetValueOrError()
        {
        }

        @Override
        public final @NonNull @Value V getWithProgress(int index, @Nullable ProgressListener progressListener) throws UserException, InternalException
        {
            // Must do this first in case it finds an error:
            _beforeGet(index, progressListener);
            String err = getError(index);
            if (err != null)
            {
                if (isImmediateData)
                    throw new InvalidImmediateValueException(StyledString.s("Invalid value: " + err), err);
                else
                    throw new UserException(err);
            }
            else
                return _getWithProgress(index, progressListener);
        }

        @OnThread(Tag.Simulation)
        protected abstract void _beforeGet(int index, @Nullable ProgressListener progressListener) throws InternalException, UserException;

        @Override
        @OnThread(Tag.Simulation)
        public final void set(int index, Either<String, @Value V> value) throws InternalException, UserException
        {
            value.eitherEx_(err -> {setError(index, err); _set(index, null);},
                v -> {errorEntries.remove(index); _set(index, v);});
        }

        @OnThread(Tag.Simulation)
        protected abstract @NonNull @Value V _getWithProgress(int index, @Nullable ProgressListener progressListener) throws UserException, InternalException;

        @OnThread(Tag.Simulation)
        protected abstract void _set(int index, @Nullable @Value V value) throws InternalException, UserException;
    }
}
