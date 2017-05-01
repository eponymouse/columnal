package records.data.columntype;

import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Created by neil on 30/10/2016.
 */
public class BlankColumnType extends ColumnType
{
    public static final BlankColumnType INSTANCE = new BlankColumnType();

    // Singleton:
    private BlankColumnType() {};

    @Override
    public int hashCode()
    {
        return 0;
    }

    @SuppressWarnings("interned")
    @Override
    public boolean equals(@Nullable Object obj)
    {
        return obj == INSTANCE;
    }

    @Override
    public String toString()
    {
        return "Blank";
    }
}
