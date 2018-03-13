package records.gui.grid;

import annotation.units.AbsColIndex;
import annotation.units.AbsRowIndex;
import javafx.geometry.BoundingBox;
import javafx.scene.Node;
import javafx.scene.shape.Rectangle;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.CellPosition;
import records.gui.grid.VirtualGridSupplier.ViewOrder;
import records.gui.grid.VirtualGridSupplier.VisibleDetails;
import records.gui.grid.VirtualGridSupplierFloating.FloatingItem;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;
import utility.gui.ResizableRectangle;

import java.util.Optional;

@OnThread(Tag.FXPlatform)
public abstract class RectangleOverlayItem extends FloatingItem<ResizableRectangle>
{
    protected RectangleOverlayItem(ViewOrder viewOrder)
    {
        super(viewOrder);
    }

    @Override
    public final Optional<BoundingBox> calculatePosition(VisibleDetails<@AbsRowIndex Integer> rowBounds, VisibleDetails<@AbsColIndex Integer> columnBounds)
    {
        Optional<RectangleBounds> optBounds = calculateBounds(rowBounds, columnBounds);
        return optBounds.map(bounds -> {
            double left = columnBounds.getItemCoord(Utility.boxCol(bounds.topLeftIncl.columnIndex));
            double top = rowBounds.getItemCoord(Utility.boxRow(bounds.topLeftIncl.rowIndex));
            // Take one pixel off so that we are on top of the right/bottom divider inset
            // rather than showing it just inside the rectangle (which looks weird)
            double right = columnBounds.getItemCoord(Utility.boxCol(bounds.bottomRightIncl.columnIndex + CellPosition.col(1))) - 1;
            double bottom = rowBounds.getItemCoord(Utility.boxRow(bounds.bottomRightIncl.rowIndex + CellPosition.row(1))) - 1;

            return new BoundingBox(
                    left, top, right - left, bottom - top
            );
        });
    }

    protected abstract Optional<RectangleBounds> calculateBounds(VisibleDetails<@AbsRowIndex Integer> rowBounds, VisibleDetails<@AbsColIndex Integer> columnBounds);

    @Override
    public final ResizableRectangle makeCell(VisibleDetails<@AbsRowIndex Integer> rowBounds, VisibleDetails<@AbsColIndex Integer> columnBounds)
    {
        ResizableRectangle r = new ResizableRectangle();
        r.setMouseTransparent(true);
        style(r, rowBounds, columnBounds);
        return r;
    }

    protected abstract void style(Rectangle r, VisibleDetails<@AbsRowIndex Integer> rowBounds, VisibleDetails<@AbsColIndex Integer> columnBounds);

    @Override
    public VirtualGridSupplier.@Nullable ItemState getItemState(CellPosition cellPosition)
    {
        return null;
    }

}
