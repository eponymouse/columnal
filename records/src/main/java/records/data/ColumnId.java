package records.data;

import org.checkerframework.checker.nullness.qual.Nullable;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.io.Serializable;

/**
 * Created by neil on 14/11/2016.
 *
 * Serializable for interface reasons, for not saving to file
 */
@OnThread(Tag.Any)
public class ColumnId implements Comparable<ColumnId>, Serializable
{
    private static final long serialVersionUID = -6813720608766860501L;
    private final String columnId;

    public ColumnId(String columnId)
    {
        this.columnId = columnId;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ColumnId tableId1 = (ColumnId) o;

        return columnId.equals(tableId1.columnId);

    }

    @Override
    public int hashCode()
    {
        return columnId.hashCode();
    }

    @Override
    public String toString()
    {
        return columnId;
    }

    public String getOutput()
    {
        return columnId;
    }

    public String getRaw()
    {
        return columnId;
    }

    @Override
    public int compareTo(ColumnId o)
    {
        return columnId.compareTo(o.columnId);
    }


}
