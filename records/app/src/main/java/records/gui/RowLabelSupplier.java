package records.gui;

import annotation.units.AbsColIndex;
import annotation.units.AbsRowIndex;
import annotation.units.TableDataRowIndex;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import javafx.beans.binding.DoubleExpression;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.BoundingBox;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.util.Duration;
import org.checkerframework.checker.nullness.qual.KeyFor;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.CellPosition;
import records.data.TableId;
import records.gui.RowLabelSupplier.LabelPane;
import records.gui.grid.RectangleBounds;
import records.gui.grid.RectangleOverlayItem;
import records.gui.grid.VirtualGrid;
import records.gui.grid.VirtualGrid.ListenerOutcome;
import records.gui.grid.VirtualGridSupplier;
import records.gui.grid.VirtualGridSupplierFloating;
import records.gui.table.TableDisplay;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FXPlatformRunnable;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;
import utility.gui.ResizableRectangle;

import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Optional;

/**
 * Row label supplier for all tables for a given grid.
 */
@OnThread(Tag.FXPlatform)
public class RowLabelSupplier extends VirtualGridSupplier<LabelPane>
{
    // For displaying the border/shadow overlays without repeating code:
    private final VirtualGridSupplierFloating virtualGridSupplierFloating;
    private final HashMap<DataDisplay, RowLabels> currentRowLabels = new HashMap<>();
    private double minRowTranslateX = 0.0;
    private @MonotonicNonNull DoubleExpression containerTranslateX;
    // Used to make sure we don't queue up multiple requests for a relayout:
    private @Nullable FXPlatformRunnable cancelRelayout;

    public void setMinRowTranslateX(double t)
    {
        this.minRowTranslateX = t;
    }
    
    private void setContainerTranslateX(DoubleExpression containerTranslateX)
    {
        if (this.containerTranslateX == null)
        {
            this.containerTranslateX = containerTranslateX;
            FXUtility.addChangeListenerPlatformNN(containerTranslateX, tx -> {
                for (RowLabels rowLabels : currentRowLabels.values())
                {
                    rowLabels.containerTranslateChanged();
                }
            });
        }
    }

    @OnThread(Tag.FXPlatform)
    private class RowLabels
    {
        private final HashMap<@TableDataRowIndex Integer, LabelPane> rowLabels;
        private final RowLabelBorder borderShadowRectangle;
        private final DataDisplay dataDisplay;
        private boolean floating = false;
        private double minTranslateX = 0.0;
        private double maxTranslateXRight;

        private RowLabels(DataDisplay dataDisplay)
        {
            this.dataDisplay = dataDisplay;
            final HashMap<@TableDataRowIndex Integer, LabelPane> rowLabelMap = new HashMap<>();
            this.rowLabels = rowLabelMap;
            borderShadowRectangle = new RowLabelBorder(rowLabelMap, dataDisplay);
            virtualGridSupplierFloating.addItem(borderShadowRectangle);
        }

        public void setFloating(boolean floating)
        {
            if (this.floating != floating)
            {
                this.floating = floating;
                for (LabelPane pane : rowLabels.values())
                {
                    pane.setFloating(floating);
                }
            }
        }

        public void containerTranslateChanged()
        {
            for (LabelPane value : rowLabels.values())
            {
                value.updateClipAndTranslate();
            }
        }

        @OnThread(Tag.FXPlatform)
        private class RowLabelBorder extends RectangleOverlayItem
        {
            private final HashMap<@TableDataRowIndex Integer, LabelPane> rowLabelMap;
            private final DataDisplay dataDisplay;

            public RowLabelBorder(HashMap<@TableDataRowIndex Integer, LabelPane> rowLabelMap, DataDisplay dataDisplay)
            {
                super(ViewOrder.TABLE_BORDER);
                this.rowLabelMap = rowLabelMap;
                this.dataDisplay = dataDisplay;
            }

            @Override
            protected Optional<Either<BoundingBox, RectangleBounds>> calculateBounds(VisibleBounds visibleBounds)
            {
                double width = rowLabelMap.values().stream().mapToDouble(p -> p.getWidth()).max().orElse(0.0);
                
                return getVisibleDataArea(visibleBounds, dataDisplay).map(leftHandColumnVis -> {
                    double right = visibleBounds.getXCoord(leftHandColumnVis.topLeftIncl.columnIndex);
                    double top = visibleBounds.getYCoord(leftHandColumnVis.topLeftIncl.rowIndex);
                    double bottom = visibleBounds.getYCoordAfter(leftHandColumnVis.bottomRightIncl.rowIndex);
                    double left = right - width;
                    
                    return Either.left(new BoundingBox(left, top, right - left, bottom - top));
                });
                
            }

            @Override
            protected void styleNewRectangle(Rectangle r, VisibleBounds visibleBounds)
            {
                r.getStyleClass().addAll("table-border-overlay", "row-label-border");
                calcClip(r);
                FXUtility.addChangeListenerPlatformNN(r.widthProperty(), w -> updateClip());
                FXUtility.addChangeListenerPlatformNN(r.heightProperty(), h -> updateClip());
            }

            @Override
            protected void sizesOrPositionsChanged(VisibleBounds visibleBounds)
            {
                updateClip();
            }

            private void updateClip()
            {
                if (getNode() != null)
                    calcClip(getNode());
            }

            @OnThread(Tag.FXPlatform)
            private void calcClip(Rectangle r)
            {
                Shape originalClip = Shape.subtract(
                    new Rectangle(-20, -20, r.getWidth() + 20, r.getHeight() + 40),
                    new Rectangle(0, 0, r.getWidth(), r.getHeight())
                );
                r.setClip(originalClip);
                //Log.debug("Set row label clip based on width: " + r.getWidth());
                
                /* // Debug code to help see what clip looks like:
                if (getPosition().columnIndex == CellPosition.col(7))
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
    }
    
    public RowLabelSupplier(VirtualGrid virtualGrid)
    {
        this.virtualGridSupplierFloating = virtualGrid.getFloatingSupplier();
    }

    @Override
    protected void layoutItems(ContainerChildren containerChildren, VisibleBounds visibleBounds, VirtualGrid virtualGrid)
    {        
        for (@KeyFor("currentRowLabels") DataDisplay dataDisplay : currentRowLabels.keySet())
        {
            Optional<RectangleBounds> visibleGrid = getVisibleDataArea(visibleBounds, dataDisplay);
            
            // Remove all non-visible:
            final RowLabels labels = currentRowLabels.get(dataDisplay);
            labels.rowLabels.entrySet().removeIf((Entry<@TableDataRowIndex Integer, LabelPane> e) -> {
                @AbsRowIndex int absIndex = dataDisplay.getAbsRowIndexFromTableRow(e.getKey());
                boolean remove = visibleGrid.map(b -> {
                    return absIndex < b.topLeftIncl.rowIndex || absIndex > b.bottomRightIncl.rowIndex;
                }).orElse(true); // Remove if nothing visible
                if (remove)
                    containerChildren.remove(e.getValue());
                return remove;
            });
            
            // Add all visible:
            visibleGrid.ifPresent(b -> {
                for (@AbsRowIndex int i = b.topLeftIncl.rowIndex; i <= b.bottomRightIncl.rowIndex; i++)
                {
                    @TableDataRowIndex int relIndex = dataDisplay.getTableRowIndexFromAbsRow(i);
                    labels.rowLabels.computeIfAbsent(relIndex, _x -> {
                        LabelPane labelPane = new LabelPane(labels);
                        labelPane.setRow(labels, relIndex);
                        Pair<DoubleExpression, DoubleExpression> trans = containerChildren.add(labelPane, ViewOrder.FLOATING);
                        setContainerTranslateX(trans.getFirst());
                        return labelPane;
                    });
                }

                // Find highest row number:
                int highestRow = labels.rowLabels.values().stream().mapToInt(p -> p.row).max().orElse(1);
                // Find number of digits, min 2:
                int numDigits = Math.max(2, Integer.toString(highestRow).length());

                @AbsColIndex int colIndex = dataDisplay.getPosition().columnIndex;
                double x = visibleBounds.getXCoord(colIndex);
                
                // The furthest we go is to the left edge of the rightmost data cell:
                labels.maxTranslateXRight = visibleBounds.getXCoord(dataDisplay.getDataDisplayBottomRightIncl().from(dataDisplay.getPosition()).columnIndex) - x;
                labels.minTranslateX = colIndex == 0 ? minRowTranslateX : 0.0;
                
                boolean anyWidthZero = false;
                
                // Set position of all:
                for (Entry<@TableDataRowIndex Integer, LabelPane> label : labels.rowLabels.entrySet())
                {
                    label.getValue().setMinDigits(numDigits);
                    double width = label.getValue().prefWidth(-1);
                    label.getValue().label.requestLayout();
                    label.getValue().requestLayout();
                    double labelPref = label.getValue().label.prefWidth(-1);
                    //Log.debug("Text \"" + label.getValue().label.getText() + "\" pref width: " + width + " label pref: " + labelPref + " min: " + label.getValue().minWidth(-1) + " scene: " + label.getValue().getScene());
                    if (width == 0)
                        anyWidthZero = true;
                    
                    @AbsRowIndex int row = dataDisplay.getAbsRowIndexFromTableRow(label.getKey());
                    double y = visibleBounds.getYCoord(row);
                    double height = visibleBounds.getYCoordAfter(row) - y;
                    
                    label.getValue().updateLayout(x, y, width, height);
                }
                
                // This happens when labels have just been added to the scene.  It's a hack, but we just come
                // back later to do another layout if this is the case:
                if (anyWidthZero && cancelRelayout == null)
                {
                    cancelRelayout = FXUtility.runAfterDelay(Duration.seconds(0.3), () -> {
                        virtualGrid.positionOrAreaChanged();
                        cancelRelayout = null;
                    });
                }
            });
            
            labels.borderShadowRectangle.updateClip();
        }
    }

    /**
     * Returns empty if no data area is visible.
     */
    @OnThread(Tag.FXPlatform)
    private Optional<RectangleBounds> getVisibleDataArea(VisibleBounds visibleBounds, DataDisplay dataDisplay)
    {
        return visibleBounds.clampVisible(
            new RectangleBounds(
                dataDisplay.getDataDisplayTopLeftIncl().from(dataDisplay.getPosition()), 
                dataDisplay.getDataDisplayBottomRightIncl().from(dataDisplay.getPosition())));
    }

    @Override
    protected @Nullable ItemState getItemState(CellPosition cellPosition, Point2D screenPosition)
    {
        return null;
    }
    /*
    @Override
    protected LabelPane makeNewItem()
    {
        return new LabelPane();
    }

    @OnThread(Tag.FX)
    @Override
    protected void adjustStyle(LabelPane item, Visible style, boolean on)
    {
        // Must set visibility on inner label, as outer item will get manipulated by VirtualGrid:
        item.label.setVisible(on);
    }

    @Override
    protected ItemState getItemState(LabelPane item, Point2D screenPos)
    {
        Bounds labelScreenBounds = item.label.localToScreen(item.label.getBoundsInLocal());
        return item.label.isVisible() && labelScreenBounds.contains(screenPos)
            ? ItemState.DIRECTLY_CLICKABLE : ItemState.NOT_CLICKABLE;
    }

    @Override
    protected void styleTogether(ImmutableMap<TableInfo, Collection<Pair<GridAreaCellPosition, LabelPane>>> visibleNodesByTable)
    {
        visibleNodesByTable.forEach((table, visibleNodesAndPos) -> {
            ImmutableList<LabelPane> visibleNodes = visibleNodesAndPos.stream().map(p -> p.getSecond()).collect(ImmutableList.toImmutableList());
            // Find highest row number:
            int highestRow = visibleNodes.stream().mapToInt(p -> p.row).max().orElse(1);
            // Find number of digits, min 2:
            int numDigits = Math.max(2, Integer.toString(highestRow).length());
            
            double minX = Double.MAX_VALUE;
            double minY = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE;
            double maxY = -Double.MAX_VALUE;
            
            for (LabelPane visibleNode : visibleNodes)
            {
                visibleNode.updateClipAndTranslate();
                
                visibleNode.setMinDigits(numDigits);
                visibleNode.label.applyCss();
                double labelWidth = visibleNode.label.prefWidth(Double.MAX_VALUE);
                // Get bounds of wrapper pane:
                minX = Math.min(visibleNode.getLayoutX() + visibleNode.getWidth() - labelWidth, minX);
                minY = Math.min(visibleNode.getLayoutY(), minY);
                maxX = Math.max(visibleNode.getLayoutX() + visibleNode.getWidth(), maxX);
                maxY = Math.max(visibleNode.getLayoutY() + visibleNode.getHeight() - 1, maxY);

                // If we want to be off-grid, translate ourselves there:
                visibleNode.leftMostColumn.set(table.tableDisplay.getPosition().columnIndex == 0);
            }
            
            if (visibleNodes.isEmpty() || visibleNodes.stream().allMatch(l -> !l.isVisible() || !l.label.isVisible()))
                table.tableDisplay.setRowLabelBounds(Optional.empty());
            else
                table.tableDisplay.setRowLabelBounds(Optional.of(new BoundingBox(minX, minY, maxX - minX, maxY - minY)));
        });
    }
    */

    @OnThread(Tag.FXPlatform)
    public void addTable(VirtualGrid virtualGrid, DataDisplay tableDisplay, boolean showAlways)
    {
        final SimpleObjectProperty<ImmutableList<Visible>> visible = new SimpleObjectProperty<>(showAlways ? ImmutableList.of(Visible.VISIBLE) : ImmutableList.of());
        RowLabels rowLabels = new RowLabels(tableDisplay);
        currentRowLabels.put(tableDisplay, rowLabels);
        virtualGrid.addSelectionListener((oldSel, newSel) -> {
            visible.set(showAlways || (newSel != null && newSel.includes(tableDisplay)) ? ImmutableList.of(Visible.VISIBLE) : ImmutableList.of());
            // Slightly lazy way to tidy up after ourselves if we get removed:
            return new Pair<>(currentRowLabels.containsKey(tableDisplay) ? ListenerOutcome.KEEP : ListenerOutcome.REMOVE, null);
        });
    }

    @Override
    protected void keyboardActivate(CellPosition cellPosition)
    {
        // We float so no keyboard activation
    }

    @OnThread(Tag.FXPlatform)
    public void removeGrid(TableDisplay display, ContainerChildren containerChildren)
    {
        @Nullable RowLabels rowLabels = currentRowLabels.remove(display);
        if (rowLabels != null)
        {
            virtualGridSupplierFloating.removeItem(rowLabels.borderShadowRectangle);
            for (LabelPane pane : rowLabels.rowLabels.values())
            {
                containerChildren.remove(pane);
            }
        }
    }

    public static enum Visible { VISIBLE }
    
    @OnThread(Tag.FXPlatform)
    public class LabelPane extends BorderPane
    {
        // Zero based row
        private @TableDataRowIndex int row;
        private final Label label = new Label();
        private final Tooltip tooltip;
        private RowLabels rowLabels;
        private int curMinDigits = 1;
        private final ResizableRectangle clip = new ResizableRectangle();
        
        public LabelPane(RowLabels rowLabels)
        {
            this.rowLabels = rowLabels;
            setClip(clip);
            setRight(label);
            setPickOnBounds(false);
            getStyleClass().add("virt-grid-row-label-pane");
            label.getStyleClass().add("virt-grid-row-label");
            label.setMaxHeight(Double.MAX_VALUE);
            BorderPane.setAlignment(label, Pos.CENTER_RIGHT);
            label.setOnContextMenuRequested(e -> {
                @Nullable ContextMenu contextMenu = rowLabels.dataDisplay.makeRowContextMenu(this.row);
                if (contextMenu != null)
                    contextMenu.show(label, e.getScreenX(), e.getScreenY());
            });
            this.tooltip = new Tooltip();
            Tooltip.install(this, tooltip);
            FXUtility.onceNotNull(label.skinProperty(), sk -> {
                Utility.later(this).updateLayout(getLayoutX(), getLayoutY(), prefWidth(-1), getHeight());
                Utility.later(this).updateClipAndTranslate();
            });
        }

        public void setFloating(boolean floating)
        {
            FXUtility.setPseudoclass(FXUtility.mouse(label), "row-header-floating", floating);
        }

        public void setRow(RowLabels rowLabels, @TableDataRowIndex int row)
        {
            this.rowLabels = rowLabels;
            this.row = row;
            // User rows begin with 1:
            label.setText(Strings.padStart(Integer.toString(row + 1), curMinDigits, ' '));
            this.tooltip.setText(Integer.toString(row + 1));
            label.requestLayout();
            rowLabels.setFloating((int)getTranslateX() != 0);
        }
        
        public void setMinDigits(int minDigits)
        {
            if (curMinDigits != minDigits)
            {
                curMinDigits = minDigits;
                // Update the label and tooltip:
                setRow(rowLabels, row);
            }
        }

        public void updateClipAndTranslate()
        {
            double width = getWidth();
            clip.setWidth(width + 20.0);
            clip.setHeight(getHeight());
            
            // We try to translate ourselves to equivalent layout X of zero, but without moving ourselves leftwards, or further across than maxTranslateXRight:
            double tx = containerTranslateX == null ? 0.0 : containerTranslateX.doubleValue();
            double min = rowLabels.minTranslateX - width;
            double clampedRight = Utility.clampIncl(min, minRowTranslateX - (getLayoutX() + tx), rowLabels.maxTranslateXRight - width);
            setTranslateX(clampedRight);
            rowLabels.setFloating(clampedRight > min + 1.0);

            //Log.debug("ClampedRight: " + clampedRight + " left: " + rowLabels.minTranslateX + " layoutX : " + getLayoutX() + " width: " + width + " container: " + tx);
        }
        
        public void updateLayout(double x, double y, double width, double height)
        {
            resizeRelocate(
                x,
                y,
                width,
                height
            );
            updateClipAndTranslate();
        }
        
        public @Nullable TableId _test_getTableId()
        {
            DataDisplay dataDisplay = rowLabels.dataDisplay;
            if (dataDisplay instanceof TableDisplay)
            {
                return ((TableDisplay)dataDisplay).getTable().getId();
            }
            return null;
        }
    }
}
