package records.gui.grid;

import records.data.CellPosition;
import records.data.Table.MessageWhenEmpty;

/**
 * One rectangular table area within a parent VirtualGrid.  Tracks position,
 * size, adjusts when resized.
 * 
 * Overlays such as line numbers are not included in the logical bounds,
 * but column headers are included.
 */
public class GridArea
{
    // The top left cell, which is probably a column header.
    private CellPosition topLeft;
    // Number of data columns, doesn't include line numbers or add-column buttons
    private int numColumns;
    
    private MessageWhenEmpty messageWhenEmpty;

    public GridArea(MessageWhenEmpty messageWhenEmpty)
    {
        this.messageWhenEmpty = messageWhenEmpty;
        // Default position:
        this.topLeft = new CellPosition(1, 1);
    }

    public CellPosition getPosition()
    {
        return topLeft;
    }
}
