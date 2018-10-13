package records.gui;

import annotation.units.AbsColIndex;
import annotation.units.AbsRowIndex;
import annotation.units.TableDataColIndex;
import annotation.units.TableDataRowIndex;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import javafx.beans.binding.DoubleExpression;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import log.Log;
import org.checkerframework.checker.nullness.qual.KeyFor;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.CellPosition;
import records.gui.RowLabelSupplier.LabelPane;
import records.gui.RowLabelSupplier.Visible;
import records.gui.grid.GridAreaCellPosition;
import records.gui.grid.RectangleBounds;
import records.gui.grid.RectangleOverlayItem;
import records.gui.grid.VirtualGrid;
import records.gui.grid.VirtualGrid.ListenerOutcome;
import records.gui.grid.VirtualGridSupplier;
import records.gui.grid.VirtualGridSupplier.ItemState;
import records.gui.grid.VirtualGridSupplier.ViewOrder;
import records.gui.grid.VirtualGridSupplierFloating;
import records.gui.grid.VirtualGridSupplierIndividual;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FXPlatformFunction;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;
import utility.gui.ResizableRectangle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Row label supplier for all tables.
 */
public class RowLabelSupplier extends VirtualGridSupplier<LabelPane>
{
    // For displaying the border/shadow overlays without repeating code:
    private final VirtualGridSupplierFloating virtualGridSupplierFloating;
    private final HashMap<DataDisplay, RowLabels> currentRowLabels = new HashMap<>();
    
    @OnThread(Tag.FXPlatform)
    private class RowLabels
    {
        private final HashMap<@TableDataRowIndex Integer, LabelPane> rowLabels;
        private final RowLabelBorder borderShadowRectangle;
        private boolean floating = false;

        private RowLabels(DataDisplay dataDisplay)
        {
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
                borderShadowRectangle.updateClip();
            }
        }

        @OnThread(Tag.FXPlatform)
        private class RowLabelBorder extends RectangleOverlayItem implements ChangeListener<Number>
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
                double width = rowLabelMap.values().stream().map(p -> p.prefWidth(-1)).findFirst().orElse(0.0);
                
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
            protected void sizesOrPositionsChanged(VisibleBounds visibleBounds)
            {
                updateClip();
            }

            @Override
            @OnThread(value = Tag.FXPlatform, ignoreParent = true)
            public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue)
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
    
    public RowLabelSupplier(VirtualGridSupplierFloating virtualGridSupplierFloating)
    {
        this.virtualGridSupplierFloating = virtualGridSupplierFloating;
    }

    @Override
    protected void layoutItems(ContainerChildren containerChildren, VisibleBounds visibleBounds)
    {        
        for (@KeyFor("currentRowLabels") DataDisplay dataDisplay : currentRowLabels.keySet())
        {
            Optional<RectangleBounds> visibleGrid = getVisibleDataArea(visibleBounds, dataDisplay);
            
            // Remove all non-visible:
            final RowLabels labels = currentRowLabels.get(dataDisplay);
            labels.rowLabels.entrySet().removeIf((Entry<@TableDataRowIndex Integer, LabelPane> e) -> {
                @AbsRowIndex int absIndex = dataDisplay.getAbsRowIndexFromTableRow(e.getKey());
                boolean remove = visibleGrid.map(b -> absIndex < b.topLeftIncl.rowIndex || absIndex > b.bottomRightIncl.rowIndex).orElse(false);
                if (remove)
                    containerChildren.remove(e.getValue());
                return remove;
            });
            
            // Add all visible:
            visibleGrid.ifPresent(b -> {
                for (@AbsRowIndex int i = b.topLeftIncl.rowIndex; i < b.bottomRightIncl.rowIndex; i++)
                {
                    @TableDataRowIndex int relIndex = dataDisplay.getTableRowIndexFromAbsRow(i);
                    labels.rowLabels.computeIfAbsent(relIndex, _x -> {
                        LabelPane labelPane = new LabelPane(labels);
                        labelPane.setRow(dataDisplay, labels, relIndex);
                        Pair<DoubleExpression, DoubleExpression> trans = containerChildren.add(labelPane, ViewOrder.FLOATING);
                        labelPane.adjustForContainerTranslate(trans.getFirst());
                        return labelPane;
                    });
                }

                // Find highest row number:
                int highestRow = labels.rowLabels.values().stream().mapToInt(p -> p.row).max().orElse(1);
                // Find number of digits, min 2:
                int numDigits = Math.max(2, Integer.toString(highestRow).length());

                // Set position of all:
                for (Entry<@TableDataRowIndex Integer, LabelPane> label : labels.rowLabels.entrySet())
                {
                    label.getValue().setMinDigits(numDigits);
                    
                    @AbsColIndex int colIndex = dataDisplay.getPosition().columnIndex;
                    double width = label.getValue().prefWidth(-1);
                    double height = 25;
                    double x = visibleBounds.getXCoord(colIndex) - width;
                    double y = visibleBounds.getYCoord(dataDisplay.getAbsRowIndexFromTableRow(label.getKey()));
                    
                    
                    label.getValue().updateLayout(visibleBounds, dataDisplay, x, y, width, height, colIndex == 0);
                }
            });
        }
    }

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
    public void removeGrid(TableDisplay display)
    {
        @Nullable RowLabels rowLabels = currentRowLabels.remove(display);
        if (rowLabels != null)
        {
            virtualGridSupplierFloating.removeItem(rowLabels.borderShadowRectangle);
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
        private @MonotonicNonNull DataDisplay tableDisplay;
        private int curMinDigits = 1;
        private final ResizableRectangle clip = new ResizableRectangle();
        private final BooleanProperty leftMostColumn = new SimpleBooleanProperty(false);

        private double containerTranslateX;
        private double maxTranslateX;

        private RowLabels rowLabels;
        
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
                if (tableDisplay != null)
                {
                    @Nullable ContextMenu contextMenu = tableDisplay.makeRowContextMenu(this.row);
                    if (contextMenu != null)
                        contextMenu.show(label, e.getScreenX(), e.getScreenY());
                }
            });
            FXUtility.addChangeListenerPlatformNN(leftMostColumn, leftMost -> {
                Utility.later(this).updateClipAndTranslate();
            });
            this.tooltip = new Tooltip();
            Tooltip.install(this, tooltip);
            FXUtility.addChangeListenerPlatformNN(translateXProperty(), tx -> {
                boolean floating = tx.intValue() != 0;
                rowLabels.setFloating(floating);
                FXUtility.setPseudoclass(FXUtility.mouse(label), "row-header-floating", floating);
            });
        }

        public boolean isTableRow(DataDisplay tableDisplay, @TableDataRowIndex int row)
        {
            return this.tableDisplay == tableDisplay && this.row == row;
        }
        
        public void setRow(DataDisplay tableDisplay, RowLabels rowLabels, @TableDataRowIndex int row)
        {
            this.tableDisplay = tableDisplay;
            this.row = row;
            // User rows begin with 1:
            label.setText(Strings.padStart(Integer.toString(row + 1), curMinDigits, ' '));
            this.tooltip.setText(Integer.toString(row + 1));
            this.rowLabels = rowLabels;
            rowLabels.setFloating((int)getTranslateX() != 0);
        }
        
        public void setMinDigits(int minDigits)
        {
            if (curMinDigits != minDigits && tableDisplay != null)
            {
                curMinDigits = minDigits;
                setRow(tableDisplay, rowLabels, row);
            }
        }

        public void updateClipAndTranslate()
        {
            clip.setWidth(getWidth() + 20.0);
            clip.setHeight(getHeight());
            
            // We try to translate ourselves to equivalent layout Y of zero, but without moving ourselves upwards, or further down than maxTranslateY:
            double clamped = Utility.clampIncl(0.0, -(getLayoutX() + containerTranslateX), maxTranslateX);
            setTranslateX(clamped);

            //Log.debug("Clamped: " + clamped + " layoutX : " + label.getLayoutX() + " us: " + getLayoutX() + " container: " + containerTranslateX);
        }
        
        public void updateLayout(VisibleBounds visibleBounds, DataDisplay dataDisplay, double x, double y, double width, double height, boolean leftMost)
        {
            // The furthest we go is to the left edge of the rightmost data cell:
            maxTranslateX = visibleBounds.getXCoord(dataDisplay.getDataDisplayBottomRightIncl().from(dataDisplay.getPosition()).columnIndex) - x - width;

            resizeRelocate(
                x,
                y,
                width,
                height
            );
            leftMostColumn.set(leftMost);
            updateClipAndTranslate();
        }

        public void adjustForContainerTranslate(DoubleExpression translateX)
        {
            containerTranslateX = translateX.get();
            FXUtility.addChangeListenerPlatformNN(translateX, tx -> {
                containerTranslateX = tx.doubleValue();
                updateClipAndTranslate();
            });
        }
    }
}
