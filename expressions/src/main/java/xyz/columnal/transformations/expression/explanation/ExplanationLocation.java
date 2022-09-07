package records.transformations.expression.explanation;

import annotation.units.TableDataRowIndex;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.ColumnId;
import xyz.columnal.data.TableId;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.Objects;
import java.util.Optional;

/**
 * A location can be:
 *  - A whole table (only tableId is present)
 *  - A whole column (tableId and columnId is present)
 *  - A specific location (tableId, columnId and rowIndex are present)
 */
@OnThread(Tag.Any)
public class ExplanationLocation
{
    public final TableId tableId;
    public final @Nullable ColumnId columnId;
    public final @Nullable @TableDataRowIndex Integer rowIndex;

    public ExplanationLocation(TableId tableId)
    {
        this.tableId = tableId;
        this.columnId = null;
        this.rowIndex = null;
    }

    public ExplanationLocation(TableId tableId, ColumnId columnId)
    {
        this.tableId = tableId;
        this.columnId = columnId;
        this.rowIndex = null;
    }
    
    public ExplanationLocation(TableId tableId, @Nullable ColumnId columnId, @Nullable @TableDataRowIndex Integer rowIndex)
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
        return Objects.equals(rowIndex, that.rowIndex) &&
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
        return tableId + (columnId != null ? ("\\" + columnId + (rowIndex != null ? ":" + rowIndex : "")) : null);
    }
}
