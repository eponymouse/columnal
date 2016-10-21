package records.data;

import org.jetbrains.annotations.NotNull;

/**
 * Created by neil on 20/10/2016.
 */
public abstract class Column<T>
{
    @NotNull public abstract T get(int index) throws Exception;

    @NotNull public abstract String getName();

    public abstract long getVersion();

    @NotNull public abstract Class<T> getType();

    public abstract boolean indexValid(int index);
}
