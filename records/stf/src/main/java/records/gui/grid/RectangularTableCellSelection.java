package records.gui.grid;

import records.data.CellPosition;

/**
 * A rectangular selection is constrained to one table
 * (TODO and its adjacent transforms?)
 * Immutable.
 */
public class RectangularTableCellSelection implements CellSelection
{
    private final CellPosition startAnchor;
    private final CellPosition curFocus;

    private final TableLimits tableLimits; 


    // Selects a single cell:
    public RectangularTableCellSelection(int rowIndex, int columnIndex, TableLimits tableLimits)
    {
        startAnchor = new CellPosition(rowIndex, columnIndex);
        curFocus = startAnchor;
        this.tableLimits = tableLimits;
    }

    private RectangularTableCellSelection(CellPosition anchor, CellPosition focus, TableLimits tableLimits)
    {
        this.startAnchor = anchor;
        this.curFocus = focus;
        this.tableLimits = tableLimits;
    }

    @Override
    public CellSelection atHome(boolean extendSelection)
    {
        CellPosition dest = new CellPosition(tableLimits.getFirstPossibleRowIncl(), curFocus.columnIndex);
        return new RectangularTableCellSelection(extendSelection ? startAnchor : dest, dest, tableLimits);
    }

    @Override
    public CellSelection atEnd(boolean extendSelection)
    {
        CellPosition dest = new CellPosition(tableLimits.getLastPossibleRowIncl(), curFocus.columnIndex);
        return new RectangularTableCellSelection(extendSelection ? startAnchor : dest, dest, tableLimits);
    }

    @Override
    public CellPosition editPosition()
    {
        // Top-left
        return curFocus;
    }

    @Override
    public CellSelection move(boolean extendSelection, int byRows, int byColumns)
    {
        CellPosition dest = new CellPosition(Math.max(tableLimits.getFirstPossibleRowIncl(), Math.min(tableLimits.getLastPossibleRowIncl() - 1, curFocus.rowIndex + byRows)),
            Math.max(tableLimits.getFirstPossibleColumnIncl(), Math.min(tableLimits.getLastPossibleColumnIncl() - 1, curFocus.columnIndex + byColumns)));
        // Move from top-left:
        return new RectangularTableCellSelection(extendSelection ? startAnchor : dest, dest, tableLimits);
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

    @Override
    public SelectionStatus columnSelectionStatus(int rowIndex)
    {
        return SelectionStatus.UNSELECTED;
    }

    static interface TableLimits
    {
        int getFirstPossibleRowIncl();
        int getLastPossibleRowIncl();
        int getFirstPossibleColumnIncl();
        int getLastPossibleColumnIncl();
    }
}
