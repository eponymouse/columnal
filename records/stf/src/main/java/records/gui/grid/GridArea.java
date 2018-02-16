package records.gui.grid;

import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import records.data.CellPosition;
import records.data.Table.MessageWhenEmpty;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * One rectangular table area within a parent VirtualGrid.  Tracks position,
 * size, adjusts when resized.
 * 
 * Overlays such as line numbers are not included in the logical bounds,
 * but column headers are included.
 */
@OnThread(Tag.FXPlatform)
public class GridArea
{
    // The top left cell, which is probably a column header.
    private CellPosition topLeft;
    // Number of data columns, doesn't include line numbers or add-column buttons
    private int numColumns;
    
    private MessageWhenEmpty messageWhenEmpty;
    private @MonotonicNonNull VirtualGrid parent;

    public GridArea(MessageWhenEmpty messageWhenEmpty)
    {
        this.messageWhenEmpty = messageWhenEmpty;
        // Default position:
        this.topLeft = new CellPosition(1, 1);
    }

    public final CellPosition getPosition()
    {
        return topLeft;
    }

    protected final void setNumColumns(int numColumns)
    {
        this.numColumns = numColumns;
        updateParent();
    }

    protected void updateParent()
    {
        if (parent != null)
            parent.positionOrAreaChanged(this);
    }

    public final void addedToGrid(VirtualGrid parent)
    {
        this.parent = parent;
        updateParent();
    }

    public final VirtualGrid _test_getParent()
    {
        if (parent == null)
            throw new RuntimeException("GridArea " + this + " has no parent");
        return parent;
    }
}
