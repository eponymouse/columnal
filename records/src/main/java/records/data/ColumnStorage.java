package records.data;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.Collections;
import java.util.List;

/**
 * Created by neil on 04/11/2016.
 */
public interface ColumnStorage<T>
{
    public int filled();
    public T get(int index) throws InternalException, UserException;
    default public void add(T item) throws InternalException
    {
        addAll(Collections.singletonList(item));
    }
    public void addAll(List<T> items) throws InternalException;

    @OnThread(Tag.Any)
    public abstract DataTypeValue getType();
}
