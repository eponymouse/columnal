package records.gui.stable;

import records.gui.stable.VirtScrollStrTextGrid.CellPosition;

/**
 * A selection of cells.  This might be, for example:
 * - A selection of one or more rows
 * - A selection of one or more columns
 * - The entire table
 * - A rectangular grid of cells within the table of at least 1x1
 */
public interface CellSelection
{
    /**
     * Gets a new selection that is the result of pressing home on this one.
     */
    public CellSelection atHome();

    /**
     * Gets a new selection that is the result of pressing end on this one.
     */
    public CellSelection atEnd(int maxRows, int maxColumns);

    /**
     * If the user was to try to edit (e.g. by pressing enter), which cell would they actually edit?
     */
    public CellPosition editPosition();

    public CellSelection move(int byRows, int byColumns, int maxRows, int maxColumns);

    /**
     * Does this selection contain the given cell?
     */
    public boolean contains(CellPosition cellPosition);
}
