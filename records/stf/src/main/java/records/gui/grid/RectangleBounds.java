package records.gui.grid;

import records.data.CellPosition;

public class RectangleBounds
{
    public final CellPosition topLeftIncl;
    public final CellPosition bottomRightIncl;
    
    public RectangleBounds(CellPosition topLeftIncl, CellPosition bottomRightIncl)
    {
        this.topLeftIncl = topLeftIncl;
        this.bottomRightIncl = bottomRightIncl;
    }

    public boolean contains(CellPosition cellPosition)
    {
        return topLeftIncl.columnIndex <= cellPosition.columnIndex && cellPosition.columnIndex <= bottomRightIncl.columnIndex
            && topLeftIncl.rowIndex <= cellPosition.rowIndex && cellPosition.rowIndex <= bottomRightIncl.rowIndex;
    }
}
