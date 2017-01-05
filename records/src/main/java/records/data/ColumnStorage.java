package records.data;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by neil on 04/11/2016.
 */
public interface ColumnStorage<T>
{
    // The amount currently stored.  Do not assume this is all available data,
    // as many columns will be loaded/calculated on demand.
    public int filled();
    public @NonNull T get(int index) throws InternalException, UserException;
    default public void add(@NonNull T item) throws InternalException
    {
        addAll(Collections.<@NonNull T>singletonList(item));
    }
    public void addAll(List<@NonNull T> items) throws InternalException;

    @OnThread(Tag.Any)
    public abstract DataTypeValue getType();

    default public List<T> getFullList(int arrayLength) throws UserException, InternalException
    {
        List<T> r = new ArrayList<>();
        for (int i = 0; i < arrayLength; i++)
        {
            r.add(get(i));
        }
        return r;
    }

    default public List<T> _test_getShrunk(int length) throws InternalException, UserException
    {
        return getFullList(length);
    }
}
