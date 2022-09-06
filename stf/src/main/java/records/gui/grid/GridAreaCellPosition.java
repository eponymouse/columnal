package records.gui.grid;

import annotation.units.AbsColIndex;
import annotation.units.AbsRowIndex;
import annotation.units.GridAreaColIndex;
import annotation.units.GridAreaRowIndex;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.CellPosition;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * A cell position is a location in the overall grid, independent
 * of any individual table bounds.
 */
/* package-visible */
@OnThread(Tag.Any)
public class GridAreaCellPosition
{
    // Both are zero-based:
    public final @GridAreaRowIndex int rowIndex;
    public final @GridAreaColIndex int columnIndex;

    public GridAreaCellPosition(@GridAreaRowIndex int rowIndex, @GridAreaColIndex int columnIndex)
    {
        this.rowIndex = rowIndex;
        this.columnIndex = columnIndex;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GridAreaCellPosition that = (GridAreaCellPosition) o;

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
    public static GridAreaCellPosition relativeFrom(CellPosition position, CellPosition topLeft)
    {
        return new GridAreaCellPosition(position.rowIndex - topLeft.rowIndex, position.columnIndex - topLeft.columnIndex);
    }

    /**
     * Given a top-left position for the grid area, returns the overall position once you apply
     * the offset given by this position.
     */
    public CellPosition from(CellPosition position)
    {
        return position.offsetByRowCols(rowIndex, columnIndex);
    }

    /*
    @SuppressWarnings("units")
    public GridAreaCellPosition offsetByRowCols(int rows, int cols)
    {
        return new GridAreaCellPosition(rowIndex + rows, columnIndex + cols);
    }
    
    public static final GridAreaCellPosition ORIGIN = new GridAreaCellPosition(row(0), col(0)); 

    @SuppressWarnings("units")
    public static @GridAreaRowIndex int row(int row)
    {
        return row;
    }

    @SuppressWarnings("units")
    public static @GridAreaColIndex int col(int col)
    {
        return col;
    }
    */
}
