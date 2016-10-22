package records.data;

import records.error.InternalException;
import records.error.UserException;

/**
 * Created by neil on 20/10/2016.
 */
public abstract class Column<T>
{
    public abstract T get(int index) throws UserException, InternalException;

    public abstract String getName();

    public abstract long getVersion();

    public abstract Class<?> getType();

    public abstract boolean indexValid(int index) throws UserException;
}
