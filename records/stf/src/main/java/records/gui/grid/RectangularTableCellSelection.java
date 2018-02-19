package records.gui.grid;

import records.data.CellPosition;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * A rectangular selection is constrained to one table
 * (TODO and its adjacent transforms?)
 * Immutable.
 */
@OnThread(Tag.FXPlatform)
public class RectangularTableCellSelection implements CellSelection
{
    private final CellPosition startAnchor;
    private final CellPosition curFocus;

    private final TableSelectionLimits tableSelectionLimits; 


    // Selects a single cell:
    public RectangularTableCellSelection(int rowIndex, int columnIndex, TableSelectionLimits tableSelectionLimits)
    {
        startAnchor = new CellPosition(rowIndex, columnIndex);
        curFocus = startAnchor;
        this.tableSelectionLimits = tableSelectionLimits;
    }

    private RectangularTableCellSelection(CellPosition anchor, CellPosition focus, TableSelectionLimits tableSelectionLimits)
    {
        this.startAnchor = anchor;
        this.curFocus = focus;
        this.tableSelectionLimits = tableSelectionLimits;
    }

    @Override
    public CellSelection atHome(boolean extendSelection)
    {
        CellPosition dest = new CellPosition(tableSelectionLimits.getFirstPossibleRowIncl(), curFocus.columnIndex);
        return new RectangularTableCellSelection(extendSelection ? startAnchor : dest, dest, tableSelectionLimits);
    }

    @Override
    public CellSelection atEnd(boolean extendSelection)
    {
        CellPosition dest = new CellPosition(tableSelectionLimits.getLastPossibleRowIncl(), curFocus.columnIndex);
        return new RectangularTableCellSelection(extendSelection ? startAnchor : dest, dest, tableSelectionLimits);
    }

    @Override
    public CellSelection move(boolean extendSelection, int byRows, int byColumns)
    {
        CellPosition dest = new CellPosition(Math.max(tableSelectionLimits.getFirstPossibleRowIncl(), Math.min(tableSelectionLimits.getLastPossibleRowIncl() - 1, curFocus.rowIndex + byRows)),
            Math.max(tableSelectionLimits.getFirstPossibleColumnIncl(), Math.min(tableSelectionLimits.getLastPossibleColumnIncl() - 1, curFocus.columnIndex + byColumns)));
        // Move from top-left:
        return new RectangularTableCellSelection(extendSelection ? startAnchor : dest, dest, tableSelectionLimits);
    }
/*
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
*/
    @OnThread(Tag.FXPlatform)
    public static interface TableSelectionLimits
    {
        int getFirstPossibleRowIncl();
        int getLastPossibleRowIncl();
        int getFirstPossibleColumnIncl();
        int getLastPossibleColumnIncl();
    }
}
