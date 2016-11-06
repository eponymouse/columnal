package records.data;

import records.error.InternalException;

import java.util.List;

/**
 * Created by neil on 04/11/2016.
 */
public interface ColumnStorage<T>
{
    public int filled();
    public T get(int index) throws InternalException;
    public void addAll(List<T> items) throws InternalException;
    public void clear();
}
