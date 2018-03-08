package records.gui.grid;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.CellPosition;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;

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
    // Copy the value of the selection to the clipboard, if that operation makes sense.
    public void doCopy();

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
     * Move the selection by that number of rows and columns.  If this would result in moving outside
     * the current selection, the new position is returned in Either.left.  Otherwise, a new selection
     * is returned in Either.right
     */
    public Either<CellPosition, CellSelection> move(boolean extendSelection, int byRows, int byColumns);

    /**
     * What cell should we scroll to, to ensure that the focus part of the selection is visible?
     */
    public CellPosition positionToEnsureInView();

    /**
     * What are the display bounds of this rectangle for drawing on screen?  Only used for drawing.
     */
    public RectangleBounds getSelectionDisplayRectangle();

    // Is the current selection the single cell supplied and nothing more?
    public boolean isExactly(CellPosition cellPosition);

    /**
     * Does the selection include the given grid area?  Can either be worked out by
     * looking at physical area, or by casting and checking against a known table.
     */
    public boolean includes(GridArea tableDisplay);
}
