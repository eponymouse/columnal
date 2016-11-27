package records.data;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.error.InternalException;

import java.util.Collections;
import java.util.List;

/**
 * Created by neil on 04/11/2016.
 */
public interface ColumnStorage<T>
{
    public int filled();
    public T get(int index) throws InternalException;
    default public void add(@Nullable T item) throws InternalException
    {
        addAll(Collections.singletonList(item));
    }
    public void addAll(List<@Nullable T> items) throws InternalException;
    public void clear();

    @SuppressWarnings("nullness")
    default public void addAllNoNull(List<T> values) throws InternalException
    {
        addAll(values);
    }
}
