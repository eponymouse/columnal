package records.data.explanation;

import annotation.units.TableDataRowIndex;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.ColumnId;
import records.data.TableId;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.Objects;

@OnThread(Tag.Any)
public class ExplanationLocation
{
    public final TableId tableId;
    public final ColumnId columnId;
    public final @TableDataRowIndex int rowIndex;

    public ExplanationLocation(TableId tableId, ColumnId columnId, @TableDataRowIndex int rowIndex)
    {
        this.tableId = tableId;
        this.columnId = columnId;
        this.rowIndex = rowIndex;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExplanationLocation that = (ExplanationLocation) o;
        return rowIndex == that.rowIndex &&
                Objects.equals(tableId, that.tableId) &&
                Objects.equals(columnId, that.columnId);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(tableId, columnId, rowIndex);
    }

    @Override
    public String toString()
    {
        return tableId + ":" + columnId + ":" + rowIndex;
    }
}
