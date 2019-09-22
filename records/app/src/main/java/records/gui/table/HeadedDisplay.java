package records.gui.table;

import annotation.units.AbsColIndex;
import annotation.units.TableDataColIndex;
import annotation.units.TableDataRowIndex;
import com.google.common.collect.ImmutableList;
import javafx.beans.binding.DoubleExpression;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.geometry.VPos;
import javafx.scene.control.ContextMenu;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.stage.Window;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.CellPosition;
import records.data.Table;
import records.data.TableId;
import records.data.TableManager;
import records.gui.EntireTableSelection;
import records.gui.ErrorableTextField;
import records.gui.TableNameTextField;
import records.gui.grid.CellSelection;
import records.gui.grid.GridArea;
import records.gui.grid.RectangleBounds;
import records.gui.grid.RectangleOverlayItem;
import records.gui.grid.VirtualGrid;
import records.gui.grid.VirtualGrid.ListenerOutcome;
import records.gui.grid.VirtualGrid.SelectionListener;
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

import java.util.ArrayList;
import java.util.Optional;

/**
 * A display that can have an editable name at the top
 */
@OnThread(Tag.FXPlatform)
public abstract class HeadedDisplay extends GridArea implements SelectionListener
{
    protected final @Nullable TableHeaderItem tableHeaderItem;

    // Version with header
    public HeadedDisplay(@Nullable TableHeaderItemParams tableHeaderItemParams, VirtualGridSupplierFloating floatingItems)
    {
        if (tableHeaderItemParams != null)
        {
            this.tableHeaderItem = new TableHeaderItem(tableHeaderItemParams.tableManager, tableHeaderItemParams.initialTableName, tableHeaderItemParams.table, tableHeaderItemParams.floatingItems);
            floatingItems.addItem(tableHeaderItem);
        }
        else
        {
            this.tableHeaderItem = null;
        }
    }

    public void cleanupFloatingItems(VirtualGridSupplierFloating floatingItems)
    {
        if (tableHeaderItem != null)
            floatingItems.removeItem(tableHeaderItem);
    }
    
    protected static class TableHeaderItemParams
    {
        @Nullable TableManager tableManager;
        TableId initialTableName;
        Table table;
        VirtualGridSupplierFloating floatingItems;

        public TableHeaderItemParams(@Nullable TableManager tableManager, TableId initialTableName, Table table, VirtualGridSupplierFloating floatingItems)
        {
            this.tableManager = tableManager;
            this.initialTableName = initialTableName;
            this.table = table;
            this.floatingItems = floatingItems;
        }
    }
    
    protected abstract @Nullable FXPlatformConsumer<TableId> renameTableOperation(Table table);

    protected class TableHeaderItem extends FloatingItem<Pane>
    {
        private final @Nullable TableManager tableManager;
        private final Table table;
        private final TableId initialTableName;
        private final VirtualGridSupplierFloating floatingItems;
        private @MonotonicNonNull ErrorableTextField<TableId> tableNameField;
        private @MonotonicNonNull BorderPane borderPane;
        private final DoubleProperty maxSize;

        @OnThread(Tag.FXPlatform)
        public TableHeaderItem(@Nullable TableManager tableManager, TableId initialTableName, Table table, VirtualGridSupplierFloating floatingItems)
        {
            super(ViewOrder.STANDARD_CELLS);
            this.maxSize = new SimpleDoubleProperty(100.0);
            this.tableManager = tableManager;
            this.table = table;
            this.initialTableName = initialTableName;
            this.floatingItems = floatingItems;
        }

        @Override
        @OnThread(Tag.FXPlatform)
        public Optional<BoundingBox> calculatePosition(VisibleBounds visibleBounds)
        {
            double x = visibleBounds.getXCoord(getPosition().columnIndex);
            double y = visibleBounds.getYCoord(getPosition().rowIndex);
            double width = visibleBounds.getXCoordAfter(getBottomRightIncl().columnIndex) - x;
            maxSize.set(width);
            // Only one row tall:
            double height = visibleBounds.getYCoordAfter(getPosition().rowIndex) - y;
            return Optional.of(new BoundingBox(
                    x,
                    y,
                    width,
                    height
            ));
        }

        @Override
        public @Nullable Pair<ItemState, @Nullable StyledString> getItemState(CellPosition cellPosition, Point2D screenPos)
        {
            if (cellPosition.rowIndex == getPosition().rowIndex
                    && getPosition().columnIndex <= cellPosition.columnIndex && cellPosition.columnIndex <= getBottomRightIncl().columnIndex)
            {
                return new Pair<>(tableNameField != null && tableNameField.isFocused() ? ItemState.EDITING : ItemState.DIRECTLY_CLICKABLE, null);
            }
            return null;
        }

        @Override
        @OnThread(Tag.FXPlatform)
        public Pane makeCell(VisibleBounds visibleBounds)
        {
            tableNameField = new TableNameTextField(tableManager, initialTableName, false, () -> withParent_(g -> g.select(new EntireTableSelection(HeadedDisplay.this, getPosition().columnIndex))));
            tableNameField.getNode().setFocusTraversable(false);
            tableNameField.sizeToFit(30.0, 30.0, maxSize);
            // We have to use PRESSED because if we do CLICKED, the field
            // will already have been focused:
            tableNameField.getNode().addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
                // Middle click may actually do something (like paste) when
                // focused, so only steal it when unfocused:
                if (e.getButton() == MouseButton.MIDDLE && tableNameField != null && !tableNameField.isFocused())
                {
                    headerMiddleClicked();
                    e.consume();
                }
            });

            @Nullable FXPlatformConsumer<TableId> renameTable = renameTableOperation(table);
            if (renameTable == null)
            {
                tableNameField.setEditable(false);
            }
            else
            {
                tableNameField.addOnFocusLoss(t -> {
                    if (t != null)
                        renameTable.consume(t);
                });
            }
            final BorderPane borderPane = new BorderPane(tableNameField.getNode());
            this.borderPane = borderPane;
            borderPane.getStyleClass().add("table-display-table-title");
            borderPane.getStyleClass().addAll(getExtraTitleStyleClasses());
            BorderPane.setAlignment(tableNameField.getNode(), Pos.CENTER_LEFT);
            BorderPane.setMargin(tableNameField.getNode(), new Insets(0, 0, 0, 8.0));
            ArrayList<MoveDestination> overlays = new ArrayList<>();
            borderPane.setOnDragDetected(e -> {
                if (overlays.isEmpty())
                {
                    Bounds originalBoundsOnScreen = borderPane.localToScreen(borderPane.getBoundsInLocal());
                    // Important that snapped is first, to match later cast:
                    overlays.add(new MoveDestinationSnapped(getPosition(), originalBoundsOnScreen, e.getScreenX(), e.getScreenY()));
                    overlays.add(new MoveDestinationFree(visibleBounds, originalBoundsOnScreen, e.getScreenX(), e.getScreenY()));
                    overlays.forEach(o -> floatingItems.addItem(o));
                    setTableDragSource(true, borderPane);
                    FXUtility.mouse(HeadedDisplay.this).updateParent();
                    withParent_(g -> g.setNudgeScroll(true));
                }
                e.consume();
            });
            borderPane.setOnMouseDragged(e -> {
                // Sometimes drag-detected arrives very late, so
                // we also create overlays in dragged if needed:
                if (overlays.isEmpty())
                {
                    Bounds originalBoundsOnScreen = borderPane.localToScreen(borderPane.getBoundsInLocal());
                    // Important that snapped is first, to match later cast:
                    overlays.add(new MoveDestinationSnapped(getPosition(), originalBoundsOnScreen, e.getScreenX(), e.getScreenY()));
                    overlays.add(new MoveDestinationFree(visibleBounds, originalBoundsOnScreen, e.getScreenX(), e.getScreenY()));
                    overlays.forEach(o -> floatingItems.addItem(o));
                    setTableDragSource(true, borderPane);
                    withParent_(g -> g.setNudgeScroll(true));
                }
                if (!overlays.isEmpty())
                {
                    overlays.forEach(o -> o.mouseMovedToScreenPos(e.getScreenX(), e.getScreenY()));
                    FXUtility.mouse(HeadedDisplay.this).updateParent();
                }
                e.consume();
            });
            borderPane.setOnMouseReleased(e -> {
                if (!overlays.isEmpty())
                {
                    CellPosition dest = ((MoveDestinationSnapped)overlays.get(0)).getDestinationPosition();
                    overlays.forEach(o -> floatingItems.removeItem(o));
                    setTableDragSource(false, borderPane);
                    // setPosition calls updateParent()
                    withParent_(p -> {
                        FXUtility.mouse(HeadedDisplay.this).setPosition(dest);
                        p.setNudgeScroll(false);
                    });
                    tableDraggedToNewPosition();
                    overlays.clear();
                }
                e.consume();
            });
            borderPane.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY && e.isStillSincePress())
                {
                    withParent_(p -> {
                        @AbsColIndex int columnIndex = p.getVisibleBounds().getNearestTopLeftToScreenPos(new Point2D(e.getScreenX(), e.getScreenY()), HPos.CENTER, VPos.CENTER).orElse(getPosition()).columnIndex;
                        p.select(new EntireTableSelection(HeadedDisplay.this, columnIndex));
                    });
                }
                else if (e.getButton() == MouseButton.MIDDLE && e.isStillSincePress())
                {
                    headerMiddleClicked();
                }
                e.consume();
            });
            borderPane.setOnContextMenuRequested(e -> {
                withParent_(g -> {
                    Point2D gridRelPos = g.getNode().screenToLocal(new Point2D(e.getScreenX(), e.getScreenY()));
                    @Nullable Pair<CellPosition, Point2D> cellPositionAt = g.getCellPositionAt(gridRelPos.getX(), gridRelPos.getY());
                    @AbsColIndex int colIndex = cellPositionAt == null ? getPosition().columnIndex : cellPositionAt.getFirst().columnIndex;
                    g.select(new EntireTableSelection(HeadedDisplay.this, colIndex));
                });
                @Nullable ContextMenu contextMenu = FXUtility.mouse(HeadedDisplay.this).getTableHeaderContextMenu();
                if (contextMenu != null)
                    contextMenu.show(borderPane, e.getScreenX(), e.getScreenY());
            });
            return borderPane;
        }

        @Override
        public void keyboardActivate(CellPosition cellPosition)
        {
            boolean correctRow = getPosition().rowIndex == cellPosition.rowIndex;
            boolean correctColumn = getPosition().columnIndex <= cellPosition.columnIndex && cellPosition.columnIndex <= getBottomRightIncl().columnIndex;

            if (correctRow && correctColumn)
            {
                @Nullable ContextMenu contextMenu = FXUtility.mouse(HeadedDisplay.this).getTableHeaderContextMenu();
                if (contextMenu != null && borderPane != null)
                    contextMenu.show(borderPane, Side.BOTTOM, 0, 0);
            }
        }

        @OnThread(Tag.FXPlatform)
        public void setSelected(boolean selected)
        {
            if (getNode() != null)
                FXUtility.setPseudoclass(getNode(), "table-selected", selected);
        }

        @OnThread(Tag.FXPlatform)
        public void setPseudoclass(String pseudoClass, boolean on)
        {
            if (getNode() != null)
                FXUtility.setPseudoclass(getNode(), pseudoClass, on);
        }
    }

    public abstract void gotoRow(Window parent, @AbsColIndex int column);

    protected abstract CellPosition getDataPosition(@TableDataRowIndex int rowIndex, @TableDataColIndex int columnIndex);
    
    protected abstract @TableDataRowIndex int getCurrentKnownRows();
    
    public double getTableNameWidth()
    {
        if (tableHeaderItem == null || tableHeaderItem.tableNameField == null)
            return 0;
        else
            return tableHeaderItem.tableNameField.getNode().getLayoutBounds().getWidth();
    }
    /**
     * Copies the given region to clipboard, if possible.
     *
     * @param dataBounds The bounds of the data region.  May extend beyond the actual data display
     *                   portion.  If null, copy all data, including that which is not loaded yet.
     */
    public abstract void doCopy(@Nullable RectangleBounds bounds);

    /**
     * Deletes the source table, if possible.
     */
    public abstract void doDelete();

    protected abstract void setTableDragSource(boolean on, BorderPane tableNamePane);

    protected @Nullable ContextMenu getTableHeaderContextMenu()
    {
        return null;
    }

    // For overridding by subclasses
    protected void headerMiddleClicked()
    {

    }

    protected ImmutableList<String> getExtraTitleStyleClasses()
    {
        return ImmutableList.of();
    }

    // For overriding in child classes
    protected void tableDraggedToNewPosition()
    {
    }

    void relayoutGrid()
    {
        withParent_(VirtualGrid::positionOrAreaChanged);
    }

    private abstract static class MoveDestination extends RectangleOverlayItem
    {
        protected final Point2D offsetFromTopLeftOfSource;
        protected Point2D lastMousePosScreen;

        protected MoveDestination(Bounds originalBoundsOnScreen, double screenX, double screenY)
        {
            super(ViewOrder.OVERLAY_ACTIVE);
            this.lastMousePosScreen = new Point2D(screenX, screenY);
            this.offsetFromTopLeftOfSource = new Point2D(screenX - originalBoundsOnScreen.getMinX(), screenY - originalBoundsOnScreen.getMinY());
        }

        public void mouseMovedToScreenPos(double screenX, double screenY)
        {
            lastMousePosScreen = new Point2D(screenX, screenY);
        }
    }

    // A destination overlay corresponding to the final snapped position and size
    private class MoveDestinationSnapped extends MoveDestination
    {
        private CellPosition destPosition;


        private MoveDestinationSnapped(CellPosition curPosition, Bounds originalBoundsOnScreen, double screenX, double screenY)
        {
            super(originalBoundsOnScreen, screenX, screenY);
            this.destPosition = curPosition;
        }

        @Override
        @OnThread(Tag.FXPlatform)
        public Optional<Either<BoundingBox, RectangleBounds>> calculateBounds(VisibleBounds visibleBounds)
        {
            Optional<CellPosition> pos = visibleBounds.getNearestTopLeftToScreenPos(lastMousePosScreen.subtract(offsetFromTopLeftOfSource), HPos.CENTER, VPos.CENTER);
            if (pos.isPresent())
            {
                destPosition = pos.get();
                destPosition = new CellPosition(Utility.maxRow(destPosition.rowIndex, CellPosition.row(1)), Utility.maxCol(destPosition.columnIndex, CellPosition.col(1)));
                return Optional.of(Either.<BoundingBox, RectangleBounds>right(new RectangleBounds(
                        destPosition,
                        destPosition.offsetByRowCols(getBottomRightIncl().rowIndex - getPosition().rowIndex, getBottomRightIncl().columnIndex - getPosition().columnIndex)
                )));
            }
            return Optional.empty();
        }

        @Override
        protected void styleNewRectangle(Rectangle r, VisibleBounds visibleBounds)
        {
            r.getStyleClass().add("move-table-dest-overlay-snapped");
        }

        public CellPosition getDestinationPosition()
        {
            return destPosition;
        }
    }

    // A destination overlay corresponding exactly to the table position and original size
    @OnThread(Tag.FXPlatform)
    private class MoveDestinationFree extends MoveDestination
    {
        private final double width;
        private final double height;

        public MoveDestinationFree(VisibleBounds visibleBounds, Bounds originalBoundsOnScreen, double screenX, double screenY)
        {
            super(originalBoundsOnScreen, screenX, screenY);
            this.width = originalBoundsOnScreen.getWidth();
            // The height is only of the header, so we must calculate ourselves:
            this.height = visibleBounds.getYCoordAfter(getBottomRightIncl().rowIndex) - visibleBounds.getYCoord(getPosition().rowIndex);
        }

        @Override
        protected Optional<Either<BoundingBox, RectangleBounds>> calculateBounds(VisibleBounds visibleBounds)
        {
            Point2D effectiveScreenTopLeft = lastMousePosScreen.subtract(offsetFromTopLeftOfSource);
            Point2D topLeft = visibleBounds.screenToLayout(effectiveScreenTopLeft);
            return Optional.of(Either.<BoundingBox, RectangleBounds>left(new BoundingBox(topLeft.getX(), topLeft.getY(), width, height)));
        }

        @Override
        protected void styleNewRectangle(Rectangle r, VisibleBounds visibleBounds)
        {
            r.getStyleClass().add("move-table-dest-overlay-free");
        }
    }

    /**
     * The table border overlay.  It is a floating overlay because otherwise the drop shadow
     * doesn't work properly.
     */
    @OnThread(Tag.FXPlatform)
    protected class TableBorderOverlay extends RectangleOverlayItem implements ChangeListener<Number>
    {
        private @MonotonicNonNull VisibleBounds lastVisibleBounds;

        public TableBorderOverlay()
        {
            super(ViewOrder.TABLE_BORDER);
        }

        @Override
        protected Optional<Either<BoundingBox, RectangleBounds>> calculateBounds(VisibleBounds visibleBounds)
        {
            // We don't use clampVisible because otherwise calculating the clip is too hard
            // So we just use a big rectangle the size of the entire table.  I don't think this
            // causes performance issues, but something to check if there are issues later on.
            CellPosition topLeft = getPosition();
            double left = visibleBounds.getXCoord(topLeft.columnIndex);
            double top = visibleBounds.getYCoord(topLeft.rowIndex);
            CellPosition bottomRight = getBottomRightIncl();
            // Take one pixel off so that we are on top of the right/bottom divider inset
            // rather than showing it just inside the rectangle (which looks weird)
            double right = visibleBounds.getXCoordAfter(bottomRight.columnIndex) - 1;
            double bottom = visibleBounds.getYCoordAfter(bottomRight.rowIndex) - 1;

            return Optional.of(Either.<BoundingBox, RectangleBounds>left(new BoundingBox(
                    left, top, right - left, bottom - top
            )));
        }

        @Override
        protected void styleNewRectangle(Rectangle r, VisibleBounds visibleBounds)
        {
            r.getStyleClass().add("table-border-overlay");
            calcClip(r, visibleBounds);
            this.lastVisibleBounds = visibleBounds;
            r.layoutXProperty().addListener(this);
            r.layoutYProperty().addListener(this);
        }

        @Override
        public void adjustForContainerTranslation(ResizableRectangle item, Pair<DoubleExpression, DoubleExpression> translateXY, boolean adding)
        {
            super.adjustForContainerTranslation(item, translateXY, adding);
            if (adding)
                translateXY.getSecond().addListener(this);
            else
                translateXY.getSecond().removeListener(this);
        }

        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue)
        {
            if (lastVisibleBounds != null)
                updateClip(lastVisibleBounds);
        }

        @Override
        protected void sizesOrPositionsChanged(VisibleBounds visibleBounds)
        {
            lastVisibleBounds = visibleBounds;
            updateClip(visibleBounds);
        }

        protected void updateClip(VisibleBounds visibleBounds)
        {
            if (getNode() != null)
                calcClip(getNode(), visibleBounds);
        }

        @OnThread(Tag.FXPlatform)
        private void calcClip(Rectangle r, VisibleBounds visibleBounds)
        {
            Shape originalClip = Shape.subtract(
                    new Rectangle(-20, -20, r.getWidth() + 40, r.getHeight() + 40),
                    new Rectangle(0, 0, r.getWidth(), r.getHeight())
            );
            // We adjust clip if we have tables touching us:
            Shape clip = withParent(p -> {
                Shape curClip = originalClip;
                for (BoundingBox neighbour : p.getTouchingRectangles(HeadedDisplay.this))
                {
                    curClip = Shape.subtract(curClip, new Rectangle(neighbour.getMinX(), neighbour.getMinY(), neighbour.getWidth(), neighbour.getHeight()));
                }
                return curClip;
            }).orElse(originalClip);
            boolean rowLabelsShowing = isShowingRowLabels();
            if (rowLabelsShowing)
            {
                @SuppressWarnings("units")
                @TableDataRowIndex int rowZero = 0;
                @SuppressWarnings("units")
                @TableDataColIndex int columnZero = 0;
                // We know where row labels will be, so easy to construct the rectangle:
                CellPosition topLeftData = getDataPosition(rowZero, columnZero);
                @SuppressWarnings("units")
                @TableDataRowIndex int oneRow = 1;
                CellPosition bottomRightData = getDataPosition(getCurrentKnownRows() - oneRow, columnZero);
                double rowStartY = visibleBounds.getYCoord(topLeftData.rowIndex);
                double rowEndY = visibleBounds.getYCoordAfter(bottomRightData.rowIndex);
                Rectangle rowLabelBounds = new Rectangle(-20, rowStartY - visibleBounds.getYCoord(getPosition().rowIndex), 20, rowEndY - rowStartY);
                //Log.debug("Rows: " + currentKnownRows + " label bounds: " + rowLabelBounds + " subtracting from " + r);
                clip = Shape.subtract(clip, rowLabelBounds);
            }

            r.setClip(clip);
            /* // Debug code to help see what clip looks like:
            if (getPosition().columnIndex == CellPosition.col(1))
            {
                // Hack to clone shape:
                Shape s = Shape.union(clip, clip);
                Scene scene = new Scene(new Group(s));
                ClipboardContent clipboardContent = new ClipboardContent();
                clipboardContent.putImage(s.snapshot(null, null));
                Clipboard.getSystemClipboard().setContent(clipboardContent);
            }
            */
        }
    }

    protected abstract boolean isShowingRowLabels();

    @Override
    @OnThread(Tag.FXPlatform)
    public Pair<VirtualGrid.ListenerOutcome, @Nullable FXPlatformConsumer<VisibleBounds>> selectionChanged(@Nullable CellSelection oldSelection, @Nullable CellSelection newSelection)
    {
        if (tableHeaderItem != null)
            tableHeaderItem.setSelected(newSelection instanceof EntireTableSelection && newSelection.includes(this));
        return new Pair<>(ListenerOutcome.KEEP, null);
    }
}
