package records.gui.highlights;

import com.google.common.collect.ImmutableList;
import javafx.geometry.BoundingBox;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.QuadCurveTo;
import javafx.scene.shape.Rectangle;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.CellPosition;
import records.gui.grid.GridArea;
import records.gui.grid.RectangleBounds;
import records.gui.grid.RectangleOverlayItem;
import records.gui.grid.VirtualGrid;
import records.gui.grid.VirtualGridSupplier.ItemState;
import records.gui.grid.VirtualGridSupplier.ViewOrder;
import records.gui.grid.VirtualGridSupplier.VisibleBounds;
import records.gui.grid.VirtualGridSupplierFloating;
import records.gui.grid.VirtualGridSupplierFloating.FloatingItem;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;
import utility.gui.ResizableRectangle;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.IntStream;

@OnThread(Tag.FXPlatform)
public class TableHighlights
{
    private ImmutableList<GridAreaHighlight> highlightedGridArea = ImmutableList.of();
    private ImmutableList<TableArrow> highlightedGridAreaArrows = ImmutableList.of();
    private @Nullable PickResult<?> picked;
    private final VirtualGrid grid;

    public TableHighlights(VirtualGrid grid)
    {
        this.grid = grid;
    }
    
    public void stopHighlightingGridArea()
    {
        VirtualGridSupplierFloating virtualGridSupplierFloating = grid.getFloatingSupplier();
        for (GridAreaHighlight gridAreaHighlight : highlightedGridArea)
        {
            virtualGridSupplierFloating.removeItem(gridAreaHighlight);
        }
        for (TableArrow highlightedGridAreaArrow : highlightedGridAreaArrows)
        {
            virtualGridSupplierFloating.removeItem(highlightedGridAreaArrow);
        }
        highlightedGridArea = ImmutableList.of();
        highlightedGridAreaArrows = ImmutableList.of();
        grid.redoLayoutAfterScroll();
    }

    public <T> @Nullable T highlightAtScreenPos(Point2D screenPos, Picker<T> picker, FXPlatformConsumer<@Nullable Cursor> setCursor)
    {
        Point2D localPos = grid.getNode().screenToLocal(screenPos);
        @Nullable Pair<CellPosition, Point2D> cellAtScreenPos = grid.getCellPositionAt(localPos.getX(), localPos.getY());
        @Nullable PickResult<?> oldPicked = picked;
        final PickResult<T> result;
        if (cellAtScreenPos == null)
        {
            result = picker.pick(null);
        }
        else
        {
            @NonNull CellPosition pos = cellAtScreenPos.getFirst();
            result = grid.getGridAreas().stream().filter(g -> g.contains(pos))
                    .flatMap(g -> Utility.streamNullable(picker.pick(new Pair<>(g, pos))))
                    .findFirst().<@Nullable Pair<RectangleBounds, T>>orElseGet(new Supplier<@Nullable PickResult<T>>()
                    {
                        @Override
                        public @Nullable PickResult<T> get()
                        {
                            return picker.pick(null);
                        }
                    });
        }
        picked = result;
        if (!Objects.equals(picked, oldPicked))
        {
            if (highlightedGridArea.size() != (picked == null ? 0: picked.highlightBounds.size()))
            {
                for (GridAreaHighlight gridAreaHighlight : highlightedGridArea)
                {
                    grid.getFloatingSupplier().removeItem(gridAreaHighlight);
                }
                highlightedGridArea = IntStream.range(0, picked == null ? 0 : picked.highlightBounds.size()).mapToObj(GridAreaHighlight::new).collect(ImmutableList.<GridAreaHighlight>toImmutableList());
                for (GridAreaHighlight gridAreaHighlight : highlightedGridArea)
                {
                    grid.getFloatingSupplier().addItem(gridAreaHighlight);
                }
            }
            if (highlightedGridAreaArrows.size() != (picked == null ? 0 : picked.arrowToScreenPos.size()))
            {
                for (TableArrow tableArrow : highlightedGridAreaArrows)
                {
                    grid.getFloatingSupplier().removeItem(tableArrow);
                }
                highlightedGridAreaArrows = IntStream.range(0, picked == null ? 0 : picked.highlightBounds.size()).mapToObj(TableArrow::new).collect(ImmutableList.<TableArrow>toImmutableList());
                for (TableArrow tableArrow : highlightedGridAreaArrows)
                {
                    grid.getFloatingSupplier().addItem(tableArrow);
                }
            }
            grid.redoLayoutAfterScroll();
        }
        setCursor.consume(picked != null ? Cursor.HAND : null);
        return result == null ? null : result.value;
    }

    public void setPseudoClasses(Node node)
    {
        if (picked != null)
        {
            HighlightType highlightType = picked.highlightType;
            FXUtility.setPseudoclass(node, "pick-select", highlightType == HighlightType.SELECT);
            FXUtility.setPseudoclass(node, "pick-source", highlightType == HighlightType.SOURCE);
        }
    }

    // Ways to highlight a grid area.
    public static enum HighlightType
    {
        // SELECT is for picking sources when you hover over the table,
        // SOURCE is for showing sources (with arrow) when hovering over table hat.
        SELECT, SOURCE;
    }

    public static class PickResult<T>
    {
        private final ImmutableList<RectangleBounds> highlightBounds;
        private final HighlightType highlightType;
        private final T value;
        private final ImmutableList<Point2D> arrowToScreenPos;

        public PickResult(RectangleBounds highlightBounds, T value)
        {
            this(ImmutableList.of(highlightBounds), HighlightType.SELECT, value, ImmutableList.of());
        }

        public PickResult(ImmutableList<RectangleBounds> highlightBounds, HighlightType highlightType, T value, ImmutableList<Point2D> arrowToScreenPos)
        {
            this.highlightBounds = highlightBounds;
            this.highlightType = highlightType;
            this.value = value;
            this.arrowToScreenPos = arrowToScreenPos;
        }

        // We don't use value in .equals(), we're only interested
        // whether it causes the same pick display.
        @Override
        public boolean equals(@Nullable Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PickResult<?> that = (PickResult<?>) o;
            return highlightBounds.equals(that.highlightBounds) &&
                    highlightType == that.highlightType &&
                    Objects.equals(arrowToScreenPos, that.arrowToScreenPos);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(highlightBounds, highlightType, arrowToScreenPos);
        }
    }

    public static interface Picker<T>
    {
        // Given a grid area and a cell contained within it, is it a valid pick?  If so,
        // return a non-null pair of the area to highlight, and some custom type T.
        public @Nullable PickResult<T> pick(@Nullable Pair<GridArea, CellPosition> gridAreaAndCell);
    }
    
    @OnThread(Tag.FXPlatform)
    private class GridAreaHighlight extends RectangleOverlayItem
    {
        private final int index;
        
        private GridAreaHighlight(int index)
        {
            super(ViewOrder.OVERLAY_ACTIVE);
            this.index = index;
        }

        @Override
        public Optional<Either<BoundingBox, RectangleBounds>> calculateBounds(VisibleBounds visibleBounds)
        {
            if (picked == null || index >= picked.highlightBounds.size())
                return Optional.empty();
            else
            {
                RectangleBounds highlightBounds = picked.highlightBounds.get(index);
                ResizableRectangle r = getNode();
                if (r != null)
                {
                    setPseudoClasses(r);
                }
                return visibleBounds.clampVisible(highlightBounds).map(b -> {
                    double x = visibleBounds.getXCoord(b.topLeftIncl.columnIndex);
                    double y = visibleBounds.getYCoord(b.topLeftIncl.rowIndex);
                    return Either.<BoundingBox, RectangleBounds>left(new BoundingBox(x, y, visibleBounds.getXCoordAfter(b.bottomRightIncl.columnIndex) - x, visibleBounds.getYCoordAfter(b.bottomRightIncl.rowIndex) - y));
                });
            }
        }

        @Override
        protected void styleNewRectangle(Rectangle r, VisibleBounds visibleBounds)
        {
            r.setMouseTransparent(true);
            r.getStyleClass().add("pick-table-overlay");
            setPseudoClasses(r);
        }
    }

    @OnThread(Tag.FXPlatform)
    private class TableArrow extends FloatingItem<Path>
    {
        private final int index;
        
        protected TableArrow(int index)
        {
            super(ViewOrder.OVERLAY_ACTIVE);
            this.index = index;
        }

        @Override
        protected Optional<BoundingBox> calculatePosition(VisibleBounds visibleBounds)
        {
            if (picked != null)
            {
                Path path = getNode();
                Point2D topLeft = setPathElements(path, visibleBounds);
                return Optional.of(new BoundingBox(topLeft.getX(), topLeft.getY(), 10, 10));
            }
            return Optional.empty();
        }

        @OnThread(Tag.FXPlatform)
        private Point2D setPathElements(@Nullable Path path, VisibleBounds visibleBounds)
        {
            if (picked != null && picked.arrowToScreenPos != null && index < picked.highlightBounds.size() && index < picked.arrowToScreenPos.size())
            {
                RectangleBounds srcBounds = picked.highlightBounds.get(index);
                Point2D arrowTo = picked.arrowToScreenPos.get(index);
                arrowTo = grid.getNode().screenToLocal(arrowTo);
                double xSrc = (visibleBounds.getXCoord(srcBounds.topLeftIncl.columnIndex) + visibleBounds.getXCoordAfter(srcBounds.bottomRightIncl.columnIndex)) / 2.0;
                double ySrc = (visibleBounds.getYCoord(srcBounds.topLeftIncl.rowIndex) + visibleBounds.getYCoordAfter(srcBounds.bottomRightIncl.rowIndex)) / 2.0;
                if (path != null)
                {
                    double headX = arrowTo.getX() - xSrc - 5;
                    double xMiddle = headX / 2.0;
                    double headY = arrowTo.getY() - ySrc - 5;
                    double yMiddle = headY / 2.0;
                    double angle = Math.atan2(yMiddle, xMiddle);
                    double headSize = 10;
                    path.getElements().setAll(new MoveTo(0, 0),
                            new QuadCurveTo(xMiddle + 50, yMiddle - 50, headX, headY),
                            new LineTo(headX + headSize * Math.cos(angle - Math.toRadians(135)), headY + headSize * Math.sin(angle - Math.toRadians(135))),
                            new MoveTo(headX, headY),
                            new LineTo(headX + headSize * Math.cos(angle + Math.toRadians(135)), headY + headSize * Math.sin(angle + Math.toRadians(135)))
                    );
                }
                return new Point2D(xSrc, ySrc);
            }
            return new Point2D(0, 0);
        }

        @Override
        protected Path makeCell(VisibleBounds visibleBounds)
        {
            Path path = new Path();
            path.setMouseTransparent(true);
            setPathElements(path, visibleBounds);
            path.getStyleClass().add("table-highlight-arrow");
            setPseudoClasses(path);
            return path;
        }

        @Override
        public @Nullable Pair<ItemState, @Nullable StyledString> getItemState(CellPosition cellPosition, Point2D screenPos)
        {
            return null;
        }

        @Override
        public void keyboardActivate(CellPosition cellPosition)
        {
        }
    }
}
