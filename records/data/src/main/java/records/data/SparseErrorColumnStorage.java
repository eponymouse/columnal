package records.data;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.error.InternalException;
import records.error.UserException;
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

    public final ImmutableList<Either<String, T>> getAllCollapsed(int fromIncl, int toExcl) throws UserException, InternalException
    {
        ImmutableList.Builder<Either<String, T>> r = ImmutableList.builderWithExpectedSize(toExcl - fromIncl);
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
        return r.build();
    }

    // Insert, but without doing errors at all, other than to add blanks where there is null
    protected abstract SimulationRunnable _insertRows(int index, List<@Nullable T> items) throws InternalException, UserException;
    

    // Remove, but without doing errors at all
    protected abstract SimulationRunnable _removeRows(int index, int count) throws InternalException, UserException;

    // Returns revert operation
    @Override
    public final SimulationRunnable insertRows(int index, List<Either<String, T>> itemsErr) throws InternalException, UserException
    {
        ArrayList<@Nullable T> items = new ArrayList<>();
        int errCount = 0;
        for (Either<String, T> errOrValue : itemsErr)
        {
            if (errOrValue.isLeft())
                errCount += 1;
            
            items.add(errOrValue.<@Nullable T>either(err -> {
                return null;
            }, v -> {
                return v;
            }));             
        }
        
        int errCountFinal = errCount;
        mapErrors(i -> i < index ? i : i + errCountFinal);
        
        SimulationRunnable revert = _insertRows(index, items);
        return () -> {
            revert.run();
            mapErrors(i -> i < index ? i : i - errCountFinal);
        };
    }
    
    // Returns revert operation
    @Override
    public final SimulationRunnable removeRows(int index, int count) throws InternalException, UserException
    {
        HashMap<Integer, String> removed = mapErrors(i -> i < index ? i : (i < index + count ? null : i - count));
        SimulationRunnable revert = _removeRows(index, count);
        return () -> {
            mapErrors(i -> i < index ? i : i + count);
            removed.forEach(this::setError);
            revert.run();
        };
    }
}
