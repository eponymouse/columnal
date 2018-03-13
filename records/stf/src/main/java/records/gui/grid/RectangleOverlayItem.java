package records.gui.grid;

import annotation.units.AbsColIndex;
import annotation.units.AbsRowIndex;
import javafx.geometry.BoundingBox;
import javafx.scene.shape.Rectangle;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.CellPosition;
import records.gui.grid.VirtualGridSupplier.ViewOrder;
import records.gui.grid.VirtualGridSupplier.VisibleBounds;
import records.gui.grid.VirtualGridSupplierFloating.FloatingItem;
import threadchecker.OnThread;
import threadchecker.Tag;
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
    public final Optional<BoundingBox> calculatePosition(VisibleBounds visibleBounds)
    {
        return calculateBounds(visibleBounds).flatMap(visibleBounds::clampVisible).map(bounds -> {
            double left = visibleBounds.getXCoord(bounds.topLeftIncl.columnIndex);
            double top = visibleBounds.getYCoord(bounds.topLeftIncl.rowIndex);
            // Take one pixel off so that we are on top of the right/bottom divider inset
            // rather than showing it just inside the rectangle (which looks weird)
            double right = visibleBounds.getXCoordAfter(bounds.bottomRightIncl.columnIndex) - 1;
            double bottom = visibleBounds.getYCoordAfter(bounds.bottomRightIncl.rowIndex) - 1;

            return new BoundingBox(
                    left, top, right - left, bottom - top
            );
        });
    }

    protected abstract Optional<RectangleBounds> calculateBounds(VisibleBounds visibleBounds);

    @Override
    public final ResizableRectangle makeCell(VisibleBounds visibleBounds)
    {
        ResizableRectangle r = new ResizableRectangle();
        r.setMouseTransparent(true);
        style(r, visibleBounds);
        return r;
    }

    protected abstract void style(Rectangle r, VisibleBounds visibleBounds);

    @Override
    public VirtualGridSupplier.@Nullable ItemState getItemState(CellPosition cellPosition)
    {
        return null;
    }

}
