package records.gui.grid;

import annotation.units.AbsColIndex;
import annotation.units.AbsRowIndex;
import org.jetbrains.annotations.NotNull;
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
    public void doCopy()
    {
        tableSelectionLimits.doCopy(calcTopLeftIncl(), calcBottomRightIncl());
    }

    @Override
    public CellPosition getActivateTarget()
    {
        return curFocus;
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
        @AbsRowIndex int targetRow = curFocus.rowIndex + byRows;
        @AbsRowIndex int clampedRow = Utility.maxRow(tableSelectionLimits.getTopLeftIncl().rowIndex, Utility.minRow(tableSelectionLimits.getBottomRightIncl().rowIndex, targetRow));
        @AbsColIndex int targetColumn = curFocus.columnIndex + byColumns;
        @AbsColIndex int clampedColumn = Utility.maxCol(tableSelectionLimits.getTopLeftIncl().columnIndex, Utility.minCol(tableSelectionLimits.getBottomRightIncl().columnIndex, targetColumn));
        
        // If we're trying to move outside without holding shift, do so:
        if ((clampedRow != targetRow || clampedColumn != targetColumn) && !extendSelection)
        {
            return Either.left(new CellPosition(targetRow, targetColumn));
        }
        else
        {
            // If we are all within bounds, or doing shift-selection, we stay in the table:
            CellPosition dest = new CellPosition(clampedRow, clampedColumn);
            // Move from top-left:
            return Either.right(new RectangularTableCellSelection(extendSelection ? startAnchor : dest, dest, tableSelectionLimits));
        }
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
                calcTopLeftIncl(),
                calcBottomRightIncl()
        );
    }

    @NotNull
    public CellPosition calcBottomRightIncl()
    {
        return new CellPosition(
            Utility.maxRow(startAnchor.rowIndex, curFocus.rowIndex),
            Utility.maxCol(startAnchor.columnIndex, curFocus.columnIndex)
        );
    }

    @NotNull
    public CellPosition calcTopLeftIncl()
    {
        return new CellPosition(
            Utility.minRow(startAnchor.rowIndex, curFocus.rowIndex),
            Utility.minCol(startAnchor.columnIndex, curFocus.columnIndex)
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
        public CellPosition getTopLeftIncl();
        public CellPosition getBottomRightIncl();
        
        public void doCopy(CellPosition topLeftIncl, CellPosition bottomRightIncl);
    }
}
