package records.gui.stable;

import records.gui.stable.VirtScrollStrTextGrid.CellPosition;
import utility.Utility;

public class RowSelection implements CellSelection
{
    private final int anchorRow;
    private final int rowIndex;

    public RowSelection(int anchorRow, int rowIndex)
    {
        this.anchorRow = anchorRow;
        this.rowIndex = rowIndex;
    }

    @Override
    public CellSelection atHome(boolean extendSelection)
    {
        return new RowSelection(extendSelection ? rowIndex : 0, 0);
    }

    @Override
    public CellSelection atEnd(boolean extendSelection, int maxRows, int maxColumns)
    {
        return new RowSelection(extendSelection ? rowIndex : maxRows - 1, maxRows - 1);
    }

    @Override
    public CellPosition editPosition()
    {
        return new CellPosition(rowIndex, 0);
    }

    @Override
    public CellSelection move(boolean extendSelection, int byRows, int byColumns, int maxRows, int maxColumns)
    {
        if (byColumns == 0)
        {
            int dest = Utility.clampIncl(0, rowIndex + byRows, maxRows - 1);
            return new RowSelection(extendSelection ? anchorRow : dest, dest);
        }
        else
        {
            // Select leftmost cell
            return new RectangularCellSelection(rowIndex, 0);
        }
    }

    @Override
    public SelectionStatus selectionStatus(CellPosition cellPosition)
    {
        return rowSelectionStatus(cellPosition.rowIndex);
    }

    @Override
    public SelectionStatus rowSelectionStatus(int index)
    {
        int minRow = Math.min(rowIndex, anchorRow);
        int maxRow = Math.max(rowIndex, anchorRow);
        if (index == rowIndex)
            return SelectionStatus.PRIMARY_SELECTION;
        else if (minRow <= index && index <= maxRow)
            return SelectionStatus.SECONDARY_SELECTION;
        else
            return SelectionStatus.UNSELECTED;
    }
}
