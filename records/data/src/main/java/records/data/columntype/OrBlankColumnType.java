package records.data.columntype;

import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A column type which also has the option to be blank.
 */
public class OrBlankColumnType extends ColumnType
{
    // The actual column type (when non-blank)
    private final ColumnType columnType;

    public OrBlankColumnType(ColumnType columnType)
    {
        this.columnType = columnType;
    }

    public Object getInner()
    {
        return columnType;
    }

    @Override
    public int hashCode()
    {
        return 1 + columnType.hashCode();
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        OrBlankColumnType that = (OrBlankColumnType) o;

        return columnType.equals(that.columnType);
    }

    @Override
    public String toString()
    {
        return columnType.toString() + "?";
    }
}
