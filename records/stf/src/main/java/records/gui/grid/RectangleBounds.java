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
}
