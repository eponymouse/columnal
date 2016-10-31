package records.importers;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.type.ColumnType;

/**
 * Created by neil on 31/10/2016.
 */
public class ColumnInfo
{
    public final ColumnType type;
    public final String title;

    public ColumnInfo(ColumnType type, String title)
    {
        this.type = type;
        this.title = title;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ColumnInfo that = (ColumnInfo) o;

        return title.equals(that.title) && type.equals(that.type);

    }

    @Override
    public int hashCode()
    {
        int result = type.hashCode();
        result = 31 * result + title.hashCode();
        return result;
    }

    @Override
    public String toString()
    {
        return "ColumnInfo{" +
            "type=" + type +
            ", title='" + title + '\'' +
            '}';
    }
}
