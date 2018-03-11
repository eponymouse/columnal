package records.gui;

import annotation.units.AbsColIndex;
import annotation.units.AbsRowIndex;
import annotation.units.TableDataRowIndex;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import log.Log;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.CellPosition;
import records.data.DataItemPosition;
import records.gui.RowLabelSupplier.LabelPane;
import records.gui.RowLabelSupplier.Visible;
import records.gui.grid.CellSelection;
import records.gui.grid.GridAreaCellPosition;
import records.gui.grid.RectangleBounds;
import records.gui.grid.VirtualGrid;
import records.gui.grid.VirtualGrid.ListenerOutcome;
import records.gui.grid.VirtualGrid.SelectionListener;
import records.gui.grid.VirtualGridSupplierIndividual;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformFunction;
import utility.Utility;
import utility.gui.FXUtility;

import java.util.Arrays;
import java.util.Collection;

public class RowLabelSupplier extends VirtualGridSupplierIndividual<LabelPane, Visible>
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
    protected ItemState getItemState(LabelPane item)
    {
        // TODO should take account of location within cell
        return item.label.isVisible() ? ItemState.DIRECTLY_CLICKABLE : ItemState.NOT_CLICKABLE;
    }

    @Override
    protected void styleTogether(ImmutableMap<GridCellInfo<LabelPane, Visible>, Collection<LabelPane>> visibleNodesByTable)
    {
        visibleNodesByTable.forEach((table, visibleNodes) -> {
            double topY = Double.MAX_VALUE;
            @Nullable LabelPane topItem = null;
            double bottomY = -Double.MAX_VALUE;
            @Nullable LabelPane bottomItem = null;
            // Find highest row number:
            int highestRow = visibleNodes.stream().mapToInt(p -> p.row).max().orElse(1);
            // Find number of digits, min 2:
            int numDigits = Math.max(2, Integer.toString(highestRow).length());
            for (LabelPane visibleNode : visibleNodes)
            {
                visibleNode.setMinDigits(numDigits);
                visibleNode.setIsTop(false);
                visibleNode.setIsBottom(false);
                // All Layout Y will be comparable, so no need to transform to screen pos:
                if (visibleNode.getLayoutY() < topY)
                {
                    topY = visibleNode.getLayoutY();
                    topItem = visibleNode;
                }
                if (visibleNode.getLayoutY() > bottomY)
                {
                    bottomY = visibleNode.getLayoutY();
                    bottomItem = visibleNode;
                }
            }
            if (topItem != null)
                topItem.setIsTop(true);
            if (bottomItem != null)
                bottomItem.setIsBottom(true);
        });
    }

    @OnThread(Tag.FXPlatform)
    public void addTable(VirtualGrid virtualGrid, TableDisplay tableDisplay)
    {
        final SimpleObjectProperty<ImmutableList<Visible>> visible = new SimpleObjectProperty<>(ImmutableList.of());
        addGrid(tableDisplay, new GridCellInfo<LabelPane, Visible>()
        {
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
        });
        virtualGrid.addSelectionListener((oldSel, newSel) -> {
            visible.set(newSel != null && newSel.includes(tableDisplay) ? ImmutableList.of(Visible.VISIBLE) : ImmutableList.of());
            // Slightly lazy way to tidy up after ourselves if we get removed:
            return hasGrid(tableDisplay) ? ListenerOutcome.KEEP : ListenerOutcome.REMOVE;
        });
    }

    public static enum Visible { VISIBLE }
    
    @OnThread(Tag.FXPlatform)
    public class LabelPane extends BorderPane
    {
        // Zero based row
        private @TableDataRowIndex int row;
        private final Label label = new Label();
        private @MonotonicNonNull TableDisplay tableDisplay;
        private int curMinDigits = 1;

        public LabelPane()
        {
            setRight(label);
            getStyleClass().add("virt-grid-row-label-pane");
            label.getStyleClass().add("virt-grid-row-label");
            label.setMaxHeight(Double.MAX_VALUE);
            BorderPane.setAlignment(label, Pos.CENTER_RIGHT);
            label.setOnContextMenuRequested(e -> {
                if (tableDisplay != null)
                    tableDisplay.makeRowContextMenu(this.row).show(label, e.getScreenX(), e.getScreenY());
            });
        }
        
        public boolean isTableRow(TableDisplay tableDisplay, @TableDataRowIndex int row)
        {
            return this.tableDisplay == tableDisplay && this.row == row;
        }
        
        public void setRow(TableDisplay tableDisplay, @TableDataRowIndex int row)
        {
            this.tableDisplay = tableDisplay;
            this.row = row;
            // User rows begin with 1:
            label.setText(Strings.padStart(Integer.toString(row + 1), curMinDigits, ' '));
        }
        
        public void setMinDigits(int minDigits)
        {
            if (curMinDigits != minDigits && tableDisplay != null)
            {
                curMinDigits = minDigits;
                setRow(tableDisplay, row);
            }
        }

        public void setIsTop(boolean top)
        {
            FXUtility.setPseudoclass(label, "top-visible-row", top);
        }

        public void setIsBottom(boolean bottom)
        {
            FXUtility.setPseudoclass(label, "bottom-visible-row", bottom);
        }
    }
}
