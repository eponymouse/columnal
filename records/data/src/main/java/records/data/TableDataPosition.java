package records.data;

import annotation.units.TableColIndex;
import annotation.units.TableRowIndex;
import org.checkerframework.checker.nullness.qual.Nullable;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * A cell position is a location in the overall grid, independent
 * of any individual table bounds.
 */
/* package-visible */
@OnThread(Tag.Any)
public class TableDataPosition
{
    // Both are zero-based:
    public final @TableRowIndex int rowIndex;
    public final @TableColIndex int columnIndex;

    public TableDataPosition(@TableRowIndex int rowIndex, @TableColIndex int columnIndex)
    {
        this.rowIndex = rowIndex;
        this.columnIndex = columnIndex;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TableDataPosition that = (TableDataPosition) o;

        if (rowIndex != that.rowIndex) return false;
        return columnIndex == that.columnIndex;
    }

    @Override
    public int hashCode()
    {
        int result = rowIndex;
        result = 31 * result + columnIndex;
        return result;
    }

    @Override
    public String toString()
    {
        return "(" + columnIndex + ", " + rowIndex + ")";
    }

    @SuppressWarnings("units")
    public TableDataPosition offsetByRowCols(int rows, int cols)
    {
        return new TableDataPosition(rowIndex + rows, columnIndex + cols);
    }
    
    @SuppressWarnings("units")
    public static @TableRowIndex int row(int row)
    {
        return row;
    }

    @SuppressWarnings("units")
    public static @TableColIndex int col(int col)
    {
        return col;
    }
}
