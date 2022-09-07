package xyz.columnal.data.columntype;

import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Created by neil on 30/10/2016.
 */
public class TextColumnType extends ColumnType
{
    @Override
    public int hashCode()
    {
        return 0;
    }

    @Override
    public boolean equals(@Nullable Object obj)
    {
        return obj != null && obj instanceof TextColumnType;
    }

    @Override
    public String toString()
    {
        return "Text";
    }
}
