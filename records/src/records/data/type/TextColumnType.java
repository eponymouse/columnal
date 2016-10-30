package records.data.type;

import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Created by neil on 30/10/2016.
 */
public class TextColumnType extends ColumnType
{
    @Override
    public boolean isText()
    {
        return true;
    }

    @Override
    public boolean equals(@Nullable Object obj)
    {
        return obj != null && obj instanceof TextColumnType;
    }
}
