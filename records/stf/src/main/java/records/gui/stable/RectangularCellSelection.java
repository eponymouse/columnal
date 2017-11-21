package records.gui.stable;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.gui.stable.VirtScrollStrTextGrid.CellPosition;

public class RectangularCellSelection implements CellSelection
{
    // Row and column index are zero-based
    private final int rowIndex;
    private final int columnIndex;
    // Counts guaranteed to be >= 1, and valid for table size
    private final int rowCount;
    private final int columnCount;

    // Selects a single cell:
    public RectangularCellSelection(int rowIndex, int columnIndex)
    {
        this.rowIndex = rowIndex;
        this.columnIndex = columnIndex;
        rowCount = 1;
        columnCount = 1;
    }

    @Override
    public CellSelection atHome()
    {
        if (rowCount == 1 && columnCount == 1)
            return new RectangularCellSelection(0, columnIndex);
        else
            return new RectangularCellSelection(rowIndex, columnIndex); // Top-left
    }

    @Override
    public CellSelection atEnd(int maxRows, int maxColumns)
    {
        if (rowCount == 1 && columnCount == 1)
            return new RectangularCellSelection(maxRows - 1, columnIndex);
        else
            return new RectangularCellSelection(rowIndex + rowCount - 1, columnIndex + columnCount - 1);
    }

    @Override
    public CellPosition editPosition()
    {
        // Top-left
        return new CellPosition(rowIndex, columnIndex);
    }

    @Override
    public CellSelection move(int byRows, int byColumns, int maxRows, int maxColumns)
    {
        // Move from top-left:
        return new RectangularCellSelection(
            Math.max(0, Math.min(maxRows - 1, rowIndex + byRows)),
            Math.max(0, Math.min(maxColumns - 1, columnIndex + byColumns))
        );
    }

    @Override
    public boolean contains(CellPosition cellPosition)
    {
        return rowIndex <= cellPosition.rowIndex && cellPosition.rowIndex < rowIndex + rowCount
            && columnIndex <= cellPosition.columnIndex && cellPosition.columnIndex < columnIndex + columnCount;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RectangularCellSelection that = (RectangularCellSelection) o;

        if (rowIndex != that.rowIndex) return false;
        if (columnIndex != that.columnIndex) return false;
        if (rowCount != that.rowCount) return false;
        return columnCount == that.columnCount;
    }

    @Override
    public int hashCode()
    {
        int result = rowIndex;
        result = 31 * result + columnIndex;
        result = 31 * result + rowCount;
        result = 31 * result + columnCount;
        return result;
    }
}
