package records.gui;

import annotation.units.AbsColIndex;
import annotation.units.AbsRowIndex;
import annotation.units.TableDataRowIndex;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.shape.Rectangle;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.CellPosition;
import records.gui.RowLabelSupplier.LabelPane;
import records.gui.RowLabelSupplier.Visible;
import records.gui.grid.GridAreaCellPosition;
import records.gui.grid.VirtualGrid;
import records.gui.grid.VirtualGrid.ListenerOutcome;
import records.gui.grid.VirtualGridSupplierIndividual;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformFunction;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;
import utility.gui.ResizableRectangle;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

public class RowLabelSupplier extends VirtualGridSupplierIndividual<LabelPane, Visible, RowLabelSupplier.TableInfo>
{
    public RowLabelSupplier()
    {
        super(ViewOrder.FLOATING, Arrays.asList(Visible.values()));
    }

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

    @OnThread(Tag.FXPlatform)
    public void addTable(VirtualGrid virtualGrid, DataDisplay tableDisplay, boolean showAlways)
    {
        final SimpleObjectProperty<ImmutableList<Visible>> visible = new SimpleObjectProperty<>(showAlways ? ImmutableList.of(Visible.VISIBLE) : ImmutableList.of());
        addGrid(tableDisplay, new TableInfo(tableDisplay, visible));
        virtualGrid.addSelectionListener((oldSel, newSel) -> {
            visible.set(showAlways || (newSel != null && newSel.includes(tableDisplay)) ? ImmutableList.of(Visible.VISIBLE) : ImmutableList.of());
            // Slightly lazy way to tidy up after ourselves if we get removed:
            return new Pair<>(hasGrid(tableDisplay) ? ListenerOutcome.KEEP : ListenerOutcome.REMOVE, null);
        });
    }

    @Override
    protected void keyboardActivate(CellPosition cellPosition)
    {
        // We float so no keyboard activation
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
        private final DoubleProperty slideOutProportion = new SimpleDoubleProperty(1.0);
        private int curMinDigits = 1;
        private final ResizableRectangle clip = new ResizableRectangle();
        private final BooleanProperty leftMostColumn = new SimpleBooleanProperty(false);

        public LabelPane()
        {
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
            FXUtility.addChangeListenerPlatformNN(slideOutProportion, f -> {
                Utility.later(this).updateClipAndTranslate();
            });
            FXUtility.addChangeListenerPlatformNN(leftMostColumn, leftMost -> {
                Utility.later(this).updateClipAndTranslate();
            });
            this.tooltip = new Tooltip();
            Tooltip.install(this, tooltip);
        }

        public boolean isTableRow(DataDisplay tableDisplay, @TableDataRowIndex int row)
        {
            return this.tableDisplay == tableDisplay && this.row == row;
        }
        
        public void setRow(DataDisplay tableDisplay, @TableDataRowIndex int row)
        {
            this.tableDisplay = tableDisplay;
            this.row = row;
            // User rows begin with 1:
            label.setText(Strings.padStart(Integer.toString(row + 1), curMinDigits, ' '));
            this.tooltip.setText(Integer.toString(row + 1));
            slideOutProportion.bind(tableDisplay.slideOutProperty());
        }
        
        public void setMinDigits(int minDigits)
        {
            if (curMinDigits != minDigits && tableDisplay != null)
            {
                curMinDigits = minDigits;
                setRow(tableDisplay, row);
            }
        }

        public void updateClipAndTranslate()
        {
            double adjustForLeftMost = leftMostColumn.get() ? getWidth() : 0;
            clip.setX(-adjustForLeftMost);
            clip.setWidth(getWidth());
            clip.setHeight(getHeight());
            double f = slideOutProportion.get();
            label.translateXProperty().set((1 - f) * label.prefWidth(Double.MAX_VALUE) - adjustForLeftMost);
        }
    }

    public class TableInfo implements VirtualGridSupplierIndividual.GridCellInfo<LabelPane, Visible>
    {
        private final DataDisplay tableDisplay;
        private final ObjectExpression<ImmutableList<Visible>> visible;

        private TableInfo(DataDisplay tableDisplay, ObjectExpression<ImmutableList<Visible>> visible)
        {
            this.tableDisplay = tableDisplay;
            this.visible = visible;
        }

        @Override
        public @Nullable GridAreaCellPosition cellAt(CellPosition cellPosition)
        {
            @AbsColIndex int columnForRowLabels = Utility.maxCol(CellPosition.col(0), tableDisplay.getPosition().columnIndex - CellPosition.col(1));
            @AbsRowIndex int topRowLabel = tableDisplay.getDataDisplayTopLeftIncl().from(tableDisplay.getPosition()).rowIndex;
            @AbsRowIndex int bottomRowLabel = tableDisplay.getDataDisplayBottomRightIncl().from(tableDisplay.getPosition()).rowIndex;
            if (cellPosition.columnIndex == columnForRowLabels && topRowLabel <= cellPosition.rowIndex && cellPosition.rowIndex <= bottomRowLabel)
            {
                return GridAreaCellPosition.relativeFrom(cellPosition, tableDisplay.getPosition());
            }
            else
            {
                return null;
            }
        }

        @Override
        public void fetchFor(GridAreaCellPosition cellPosition, FXPlatformFunction<CellPosition, @Nullable LabelPane> getCell)
        {
            @Nullable LabelPane labelPane = getCell.apply(cellPosition.from(tableDisplay.getPosition()));
            if (labelPane != null)
                labelPane.setRow(tableDisplay, tableDisplay.getRowIndexWithinTable(cellPosition.rowIndex));
        }

        @Override
        public ObjectExpression<? extends Collection<Visible>> styleForAllCells()
        {
            return visible;
        }

        @Override
        public boolean checkCellUpToDate(GridAreaCellPosition cellPosition, LabelPane cell)
        {
            return cell.isTableRow(tableDisplay, tableDisplay.getRowIndexWithinTable(cellPosition.rowIndex))
                && cellAt(tableDisplay.getPosition().offsetByRowCols(cellPosition.rowIndex, cellPosition.columnIndex)) != null;
        }
    }
}
