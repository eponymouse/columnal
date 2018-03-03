package records.gui.grid;

import annotation.units.AbsColIndex;
import annotation.units.AbsRowIndex;
import records.data.CellPosition;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Utility;

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
    public RectangularTableCellSelection(@AbsRowIndex int rowIndex, @AbsColIndex int columnIndex, TableSelectionLimits tableSelectionLimits)
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
        CellPosition dest = new CellPosition(tableSelectionLimits.getTopLeftIncl().rowIndex, curFocus.columnIndex);
        return new RectangularTableCellSelection(extendSelection ? startAnchor : dest, dest, tableSelectionLimits);
    }

    @Override
    public CellSelection atEnd(boolean extendSelection)
    {
        CellPosition dest = new CellPosition(tableSelectionLimits.getBottomRightIncl().rowIndex, curFocus.columnIndex);
        return new RectangularTableCellSelection(extendSelection ? startAnchor : dest, dest, tableSelectionLimits);
    }

    @Override
    public Either<CellPosition, CellSelection> move(boolean extendSelection, int _byRows, int _byColumns)
    {
        @AbsRowIndex int byRows = CellPosition.row(_byRows);
        @AbsColIndex int byColumns = CellPosition.col(_byColumns);
        CellPosition dest = new CellPosition(Utility.maxRow(tableSelectionLimits.getTopLeftIncl().rowIndex, Utility.minRow(tableSelectionLimits.getBottomRightIncl().rowIndex, curFocus.rowIndex + byRows)),
            Utility.maxCol(tableSelectionLimits.getTopLeftIncl().columnIndex, Utility.minCol(tableSelectionLimits.getBottomRightIncl().columnIndex, curFocus.columnIndex + byColumns)));
        // Move from top-left:
        return Either.right(new RectangularTableCellSelection(extendSelection ? startAnchor : dest, dest, tableSelectionLimits));
    }

    @Override
    public CellPosition positionToEnsureInView()
    {
        return curFocus;
    }

    @Override
    public RectangleBounds getSelectionDisplayRectangle()
    {
        return new RectangleBounds(
            new CellPosition(
                Utility.minRow(startAnchor.rowIndex, curFocus.rowIndex),
                Utility.minCol(startAnchor.columnIndex, curFocus.columnIndex)
            ),
            new CellPosition(
                Utility.maxRow(startAnchor.rowIndex, curFocus.rowIndex),
                Utility.maxCol(startAnchor.columnIndex, curFocus.columnIndex)
            )
        );
    }

    @Override
    public boolean isExactly(CellPosition cellPosition)
    {
        return startAnchor.equals(cellPosition) && curFocus.equals(cellPosition);
    }

    @Override
    public boolean includes(GridArea tableDisplay)
    {
        // Rely on non-overlap of grid areas, and the way that our selection won't span multiple tables:
        return tableDisplay.contains(startAnchor); 
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
        CellPosition getTopLeftIncl();
        CellPosition getBottomRightIncl();
    }
}
