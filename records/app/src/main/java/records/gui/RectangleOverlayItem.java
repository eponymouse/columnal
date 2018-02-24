package records.gui;

import com.google.common.collect.ImmutableList;
import javafx.geometry.BoundingBox;
import javafx.scene.Node;
import javafx.scene.shape.Rectangle;
import records.data.CellPosition;
import records.gui.grid.VirtualGridSupplier.ViewOrder;
import records.gui.grid.VirtualGridSupplier.VisibleDetails;
import records.gui.grid.VirtualGridSupplierFloating.FloatingItem;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;

import java.util.Optional;

@OnThread(Tag.FXPlatform)
public abstract class RectangleOverlayItem implements FloatingItem
{
    @Override
    public final Optional<BoundingBox> calculatePosition(VisibleDetails rowBounds, VisibleDetails columnBounds)
    {
        Optional<RectangleBounds> optBounds = calculateBounds(rowBounds, columnBounds);
        return optBounds.map(bounds -> {
            double left = columnBounds.getItemCoord(bounds.topLeftIncl.columnIndex);
            double top = rowBounds.getItemCoord(bounds.topLeftIncl.rowIndex);
            // Take one pixel off so that we are on top of the right/bottom divider inset
            // rather than showing it just inside the rectangle (which looks weird)
            double right = columnBounds.getItemCoord(bounds.bottomRightIncl.columnIndex + 1) - 1;
            double bottom = rowBounds.getItemCoord(bounds.bottomRightIncl.rowIndex + 1) - 1;

            return new BoundingBox(
                    left, top, right - left, bottom - top
            );
        });
    }

    protected abstract Optional<RectangleBounds> calculateBounds(VisibleDetails rowBounds, VisibleDetails columnBounds);

    @Override
    public final Pair<ViewOrder, Node> makeCell()
    {
        Rectangle r = new Rectangle() {
            @Override
            public void resize(double width, double height)
            {
                setWidth(width);
                setHeight(height);
            }

            @Override
            public boolean isResizable()
            {
                return true;
            }
        };
        r.setMouseTransparent(true);
        style(r);
        return new Pair<>(ViewOrder.OVERLAY, r);
    }

    protected abstract void style(Rectangle r);

    public static class RectangleBounds
    {
        private final CellPosition topLeftIncl;
        private final CellPosition bottomRightIncl;
        
        public RectangleBounds(CellPosition topLeftIncl, CellPosition bottomRightIncl)
        {
            this.topLeftIncl = topLeftIncl;
            this.bottomRightIncl = bottomRightIncl;
        }
    }
}
