package records.data;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Column.ProgressListener;
import records.data.datatype.DataTypeValue.GetValue;
import records.error.InternalException;
import records.error.InvalidImmediateValueException;
import records.error.UserException;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.SimulationRunnable;
import utility.Utility;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.UnaryOperator;

public abstract class SparseErrorColumnStorage<T> implements ColumnStorage<T>
{
    private HashMap<Integer, String> errorEntries = new HashMap<>();
    
    protected final @Nullable String getError(int row)
    {
        return errorEntries.get(row);
    }
    
    protected final void setError(int row, String error)
    {
        errorEntries.put(row, error);
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
        ArrayList<@Nullable T> items = new ArrayList<>();
        for (Either<String, T> errOrValue : itemsErr)
        {
            items.add(errOrValue.<@Nullable T>either(err -> {
                return null;
            }, v -> {
                return v;
            }));             
        }
        
        int itemsSize = items.size();
        mapErrors(i -> i < index ? i : i + itemsSize);
        
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
    
    protected abstract class GetValueOrError<V> implements GetValue<V>
    {
        @OnThread(Tag.Any)
        public GetValueOrError()
        {
        }

        @NonNull
        @Override
        public final V getWithProgress(int index, @Nullable ProgressListener progressListener) throws UserException, InternalException
        {
            String err = getError(index);
            if (err != null)
                throw new InvalidImmediateValueException(StyledString.s("Invalid value: " + err), err);
            else
                return _getWithProgress(index, progressListener);
        }

        @Override
        @OnThread(Tag.Simulation)
        public final void set(int index, Either<String, V> value) throws InternalException, UserException
        {
            value.eitherEx_(err -> {setError(index, err); _set(index, null);},
                v -> {errorEntries.remove(index); _set(index, v);});
        }

        protected abstract @NonNull V _getWithProgress(int index, @Nullable ProgressListener progressListener) throws UserException, InternalException;

        @OnThread(Tag.Simulation)
        protected abstract void _set(int index, @Nullable V value) throws InternalException, UserException;
    }
}
