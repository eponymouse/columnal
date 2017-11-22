package records.gui.stable;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.gui.stable.VirtScrollStrTextGrid.CellPosition;

public class RectangularCellSelection implements CellSelection
{
    private final CellPosition startAnchor;
    private final CellPosition curFocus;

    // Selects a single cell:
    public RectangularCellSelection(int rowIndex, int columnIndex)
    {
        startAnchor = curFocus = new CellPosition(rowIndex, columnIndex);
    }

    private RectangularCellSelection(CellPosition anchor, CellPosition focus)
    {
        this.startAnchor = anchor;
        this.curFocus = focus;
    }

    @Override
    public CellSelection atHome(boolean extendSelection)
    {
        CellPosition dest = new CellPosition(0, curFocus.columnIndex);
        return new RectangularCellSelection(extendSelection ? startAnchor : dest, dest);
    }

    @Override
    public CellSelection atEnd(boolean extendSelection, int maxRows, int maxColumns)
    {
        CellPosition dest = new CellPosition(maxRows - 1, curFocus.columnIndex);
        return new RectangularCellSelection(extendSelection ? startAnchor : dest, dest);
    }

    @Override
    public CellPosition editPosition()
    {
        // Top-left
        return curFocus;
    }

    @Override
    public CellSelection move(boolean extendSelection, int byRows, int byColumns, int maxRows, int maxColumns)
    {
        CellPosition dest = new CellPosition(Math.max(0, Math.min(maxRows - 1, curFocus.rowIndex + byRows)),
            Math.max(0, Math.min(maxColumns - 1, curFocus.columnIndex + byColumns)));
        // Move from top-left:
        return new RectangularCellSelection(extendSelection ? startAnchor : dest, dest);
    }

    @Override
    public SelectionStatus selectionStatus(CellPosition cellPosition)
    {
        if (cellPosition.equals(curFocus))
            return SelectionStatus.PRIMARY_SELECTION;

        int minRow = Math.min(startAnchor.rowIndex, curFocus.rowIndex);
        int maxRow = Math.max(startAnchor.rowIndex, curFocus.rowIndex);
        int minColumn = Math.min(startAnchor.columnIndex, curFocus.columnIndex);
        int maxColumn = Math.max(startAnchor.columnIndex, curFocus.columnIndex);
        
        return (minRow <= cellPosition.rowIndex && cellPosition.rowIndex <= maxRow
            && minColumn <= cellPosition.columnIndex && cellPosition.columnIndex <= maxColumn) ? SelectionStatus.SECONDARY_SELECTION : SelectionStatus.UNSELECTED;
    }

    @Override
    public SelectionStatus rowSelectionStatus(int rowIndex)
    {
        return SelectionStatus.UNSELECTED;
    }

}
