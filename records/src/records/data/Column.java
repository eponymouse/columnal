package records.data;

import org.checkerframework.checker.nullness.qual.NonNull;
import records.error.InternalException;
import records.error.UserException;

import java.util.List;
import java.util.Optional;

/**
 * Created by neil on 20/10/2016.
 */
public abstract class Column
{
    public abstract Object get(int index) throws UserException, InternalException;

    public abstract String getName();

    public abstract long getVersion();

    public abstract Class<?> getType();

    public abstract boolean indexValid(int index) throws UserException;

    // If supported, get number of distinct values quickly:
    public Optional<List<@NonNull ?>> fastDistinct() throws UserException
    {
        return Optional.empty();
    }
}
