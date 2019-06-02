package records.transformations.expression.explanation;

import annotation.units.TableDataRowIndex;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.ColumnId;
import records.data.TableId;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.Objects;
import java.util.Optional;

@OnThread(Tag.Any)
public class ExplanationLocation
{
    public final TableId tableId;
    public final ColumnId columnId;
    public final Optional<@TableDataRowIndex Integer> rowIndex;

    public ExplanationLocation(TableId tableId, ColumnId columnId)
    {
        this.tableId = tableId;
        this.columnId = columnId;
        this.rowIndex = Optional.empty();
    }
    
    public ExplanationLocation(TableId tableId, ColumnId columnId, @TableDataRowIndex int rowIndex)
    {
        this.tableId = tableId;
        this.columnId = columnId;
        this.rowIndex = Optional.of(rowIndex);
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
        return tableId + "\\" + columnId + (rowIndex.isPresent() ? ":" + rowIndex.get() : "");
    }
}
