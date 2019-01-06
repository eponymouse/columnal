package records.data;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Column.ProgressListener;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.SimulationRunnable;

import java.util.ArrayList;
import java.util.Collections;
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
        addAll(Stream.<Either<String, @NonNull T>>of(Either.right(item)));
    }
    public void addAll(Stream<Either<String, @NonNull T>> items) throws InternalException;

    @OnThread(Tag.Any)
    public abstract DataTypeValue getType();

    default public ImmutableList<Either<String, T>> getAllCollapsed(int fromIncl, int toExcl) throws UserException, InternalException
    {
        List<Either<String, T>> r = new ArrayList<>(toExcl - fromIncl);
        for (int i = fromIncl; i < toExcl; i++)
        {
            @SuppressWarnings("unchecked")
            T collapsed = (T) getType().getCollapsed(i);
            r.add(Either.right(collapsed));
        }
        return ImmutableList.copyOf(r);
    }

    // Returns revert operation
    public SimulationRunnable insertRows(int index, List<Either<String, T>> items) throws InternalException, UserException;
    // Returns revert operation
    public SimulationRunnable removeRows(int index, int count) throws InternalException, UserException;

    public static interface BeforeGet<S extends ColumnStorage<?>>
    {
        public void beforeGet(S storage, int index, @Nullable ProgressListener progressListener) throws InternalException, UserException;
    }
}
