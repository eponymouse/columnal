package records.gui.grid;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.CellPosition;

import java.util.Objects;

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

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RectangleBounds that = (RectangleBounds) o;
        return Objects.equals(topLeftIncl, that.topLeftIncl) &&
            Objects.equals(bottomRightIncl, that.bottomRightIncl);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(topLeftIncl, bottomRightIncl);
    }

    @Override
    public String toString()
    {
        return "[" + topLeftIncl + " - " + bottomRightIncl + "]";
    }
}
