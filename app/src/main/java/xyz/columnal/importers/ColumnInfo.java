package xyz.columnal.importers;

import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.ColumnId;
import xyz.columnal.data.columntype.ColumnType;

/**
 * Created by neil on 31/10/2016.
 */
public class ColumnInfo
{
    public final ColumnType type;
    public final ColumnId title;

    public ColumnInfo(ColumnType type, ColumnId title)
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
            "columntype=" + type +
            ", title='" + title + '\'' +
            '}';
    }
}
