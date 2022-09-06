package records.data;

import annotation.units.AbsColIndex;
import annotation.units.AbsRowIndex;
import org.checkerframework.checker.nullness.qual.Nullable;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * A cell position is a location in the overall grid, independent
 * of any individual table bounds.
 */
/* package-visible */
@OnThread(Tag.Any)
public class CellPosition
{
    // Both are zero-based:
    public final @AbsRowIndex int rowIndex;
    public final @AbsColIndex int columnIndex;

    public CellPosition(@AbsRowIndex int rowIndex, @AbsColIndex int columnIndex)
    {
        this.rowIndex = rowIndex;
        this.columnIndex = columnIndex;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CellPosition that = (CellPosition) o;

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
    public CellPosition offsetByRowCols(int rows, int cols)
    {
        return new CellPosition(rowIndex + rows, columnIndex + cols);
    }
    
    public static final CellPosition ORIGIN = new CellPosition(row(0), col(0)); 

    @SuppressWarnings("units")
    public static @AbsRowIndex int row(int row)
    {
        return row;
    }

    @SuppressWarnings("units")
    public static @AbsColIndex int col(int col)
    {
        return col;
    }
}
