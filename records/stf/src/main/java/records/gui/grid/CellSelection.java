package records.gui.grid;

import records.data.CellPosition;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * A selection of cells.  This might be, for example:
 * - A selection of one or more rows
 * - A selection of one or more columns
 * - The entire table
 * - A rectangular grid of cells within the table of at least 1x1
 */
@OnThread(Tag.FXPlatform)
public interface CellSelection
{
    // Primary selection means the single cell/row/column being moved around,
    // secondary selection means cells that are also selected but not primary.
    public static enum SelectionStatus { UNSELECTED, SECONDARY_SELECTION, PRIMARY_SELECTION}

    /**
     * Gets a new selection that is the result of pressing home on this one.
     */
    public CellSelection atHome(boolean extendSelection);

    /**
     * Gets a new selection that is the result of pressing end on this one.
     */
    public CellSelection atEnd(boolean extendSelection);

    /**
     * If the user was to try to edit (e.g. by pressing enter), which cell would they actually edit?
     */
    public CellPosition editPosition();

    public CellSelection move(boolean extendSelection, int byRows, int byColumns);

    /**
     * Does this selection contain the given cell?
     */
    public SelectionStatus selectionStatus(CellPosition cellPosition);

    /**
     * Does the selection contain the given row header?
     */
    public SelectionStatus rowSelectionStatus(int rowIndex);

    /**
     * Does the selection contain the given column header?
     */
    public SelectionStatus columnSelectionStatus(int columnIndex);
}
