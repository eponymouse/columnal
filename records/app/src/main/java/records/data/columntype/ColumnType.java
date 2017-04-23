package records.data.columntype;

import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Created by neil on 30/10/2016.
 */
public abstract class ColumnType
{
    public static final BlankColumnType BLANK = BlankColumnType.INSTANCE;

    @Override
    public abstract int hashCode();

    @Override
    public abstract boolean equals(@Nullable Object obj);

    @Override
    public abstract String toString();
}
