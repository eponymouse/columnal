/*
 * Columnal: Safer, smoother data table processing.
 * Copyright (c) Neil Brown, 2016-2020, 2022.
 *
 * This file is part of Columnal.
 *
 * Columnal is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Columnal is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for 
 * more details.
 *
 * You should have received a copy of the GNU General Public License along 
 * with Columnal. If not, see <https://www.gnu.org/licenses/>.
 */

package xyz.columnal.gui;

import annotation.units.AbsColIndex;
import com.google.common.collect.ImmutableList;
import javafx.beans.binding.ObjectExpression;
import javafx.geometry.Point2D;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import xyz.columnal.log.Log;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.CellPosition;
import xyz.columnal.data.TableOperations.AppendRows;
import xyz.columnal.gui.table.DataCellSupplier.CellStyle;
import xyz.columnal.gui.grid.GridAreaCellPosition;
import xyz.columnal.gui.grid.RectangleBounds;
import xyz.columnal.gui.grid.VirtualGrid;
import xyz.columnal.gui.grid.VirtualGridSupplierIndividual;
import xyz.columnal.gui.grid.VirtualGridSupplierIndividual.GridCellInfo;
import xyz.columnal.gui.table.app.TableDisplay;
import xyz.columnal.styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.function.fx.FXPlatformFunction;
import xyz.columnal.utility.function.fx.FXPlatformRunnable;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Workers;
import xyz.columnal.utility.Workers.Priority;
import xyz.columnal.utility.gui.FXUtility;

import java.util.Arrays;
import java.util.Collection;
import java.util.OptionalDouble;
import java.util.WeakHashMap;

@OnThread(Tag.FXPlatform)
public class ExpandTableArrowSupplier extends VirtualGridSupplierIndividual<Button, CellStyle, GridCellInfo<Button, CellStyle>>
{
    public static final String RIGHT_ARROW = "\u27F6";
    public static final String DOWN_ARROW = "\u2193";
    private WeakHashMap<Button, Pair<TableDisplay, CellStyle>> buttonTableDisplaysAndHoverStyles = new WeakHashMap<>();
    
    public ExpandTableArrowSupplier()
    {
        super(Arrays.asList(CellStyle.values()));
    }
    
    @Override
    protected Button makeNewItem(VirtualGrid virtualGrid)
    {
        Button button = new Button();
        // By default buttons have quite constrained sizes.  Let them take on any size that the grid specifies:
        button.setMinWidth(0.0);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setMinHeight(0.0);
        button.setMaxHeight(Double.MAX_VALUE);
        button.getStyleClass().add("expand-arrow");
        
        // We only want one hover listener:
        FXUtility.addChangeListenerPlatformNN(button.hoverProperty(), hover -> {
            Pair<TableDisplay, CellStyle> tableDisplayAndHoverStyle = buttonTableDisplaysAndHoverStyles.get(button);
            if (tableDisplayAndHoverStyle == null)
                return;
            if (hover)
                tableDisplayAndHoverStyle.getFirst().addCellStyle(tableDisplayAndHoverStyle.getSecond());
            else
                tableDisplayAndHoverStyle.getFirst().removeCellStyle(tableDisplayAndHoverStyle.getSecond());
        });
        
        return button;
    }

    @Override
    protected @OnThread(Tag.FX) void adjustStyle(Button item, CellStyle style, boolean on)
    {
        style.applyStyle(item, on);
    }

    @Override
    public OptionalDouble getPrefColumnWidth(@AbsColIndex int colIndex)
    {
        return getItemsInColumn(colIndex).stream().mapToDouble(b -> b.prefWidth(-1)).max();
    }

    @Override
    protected @Nullable Pair<ItemState, @Nullable StyledString> getItemState(Button item, Point2D screenPos)
    {
        return new Pair<>(ItemState.DIRECTLY_CLICKABLE, null);
    }

    public void addTable(TableDisplay tableDisplay, @Nullable FXPlatformRunnable addColumn, boolean addRows)
    {
        super.addGrid(tableDisplay, new GridCellInfo<Button, CellStyle>()
        {
            @Override
            public @Nullable GridAreaCellPosition cellAt(CellPosition cellPosition)
            {
                GridAreaCellPosition gridAreaCellPosition = GridAreaCellPosition.relativeFrom(cellPosition, tableDisplay.getPosition());
                // Work out if the cell is either just to the right, or just below:
                if (hasAddColumnArrow(gridAreaCellPosition) || hasAddRowArrow(gridAreaCellPosition))
                {
                    return gridAreaCellPosition;
                }
                else
                    return null;
            }

            @Override
            public ImmutableList<RectangleBounds> getCellBounds()
            {
                CellPosition tablePos = tableDisplay.getMostRecentPosition();
                CellPosition bottomRightDataPos = tableDisplay.getDataDisplayBottomRightIncl().from(tablePos);
                int numCols = tableDisplay.getDisplayColumns().size();
                return ImmutableList.of(
                    // Right-hand side arrows:
                    new RectangleBounds(new CellPosition(tablePos.rowIndex + CellPosition.row(1), bottomRightDataPos.columnIndex + CellPosition.col(1)), new CellPosition(bottomRightDataPos.rowIndex, bottomRightDataPos.columnIndex + CellPosition.col(1))),
                    // Bottom arrows:
                    new RectangleBounds(new CellPosition(bottomRightDataPos.rowIndex + CellPosition.row(1), tablePos.columnIndex), new CellPosition(bottomRightDataPos.rowIndex + CellPosition.row(1), bottomRightDataPos.columnIndex))
                );
            }

            @OnThread(Tag.FXPlatform)
            private boolean hasAddRowArrow(GridAreaCellPosition cellPosition)
            {
                return addRows
                    && (!tableDisplay.getDisplayColumns().isEmpty() && !tableDisplay.getColumns().isEmpty())
                    && cellPosition.rowIndex == tableDisplay.getDataDisplayBottomRightIncl().rowIndex + 1
                    && cellPosition.columnIndex >= tableDisplay.getDataDisplayTopLeftIncl().columnIndex
                    && cellPosition.columnIndex <= tableDisplay.getDataDisplayBottomRightIncl().columnIndex;
            }

            @OnThread(Tag.FXPlatform)
            public boolean hasAddColumnArrow(GridAreaCellPosition cellPosition)
            {
                return addColumn != null
                    && !tableDisplay.getDisplayColumns().isEmpty()
                    && cellPosition.columnIndex == tableDisplay.getDataDisplayBottomRightIncl().columnIndex + 1
                    && (cellPosition.rowIndex >= 1 && cellPosition.rowIndex <= tableDisplay.getDataDisplayBottomRightIncl().rowIndex);
            }

            @Override
            public boolean checkCellUpToDate(GridAreaCellPosition cellPosition, Button item)
            {
                // The only thing we really need to check is whether a column arrow
                // has become row, or vice versa:
                return Utility.getIfPresent(buttonTableDisplaysAndHoverStyles, item).map(p -> p.getFirst() == tableDisplay).orElse(false)
                    && (item.getText().equals(RIGHT_ARROW) == hasAddColumnArrow(cellPosition))
                    && (item.getText().equals(DOWN_ARROW) == hasAddRowArrow(cellPosition));
            }

            @Override
            public void fetchFor(GridAreaCellPosition cellPosition, FXPlatformFunction<CellPosition, @Nullable Button> getCell, FXPlatformRunnable scheduleStyleTogether)
            {
                @Nullable Button item = getCell.apply(cellPosition.from(tableDisplay.getPosition()));
                if (item == null)
                    return;
                
                if (hasAddColumnArrow(cellPosition))
                {
                    FXUtility.setPseudoclass(item, "expand-right-header", cellPosition.rowIndex < tableDisplay.getDataDisplayTopLeftIncl().rowIndex);
                    FXUtility.setPseudoclass(item, "expand-right", true);
                    FXUtility.setPseudoclass(item, "expand-down", false);
                    item.setTooltip(new Tooltip("Click to add column"));
                    item.setVisible(true);
                    item.setText(RIGHT_ARROW);
                    item.setOnAction(e -> {
                        // Shouldn't be null here, but satisfy checker:
                        if (addColumn != null)
                            addColumn.run();
                    });
                    buttonTableDisplaysAndHoverStyles.put(item, new Pair<>(tableDisplay, CellStyle.HOVERING_EXPAND_RIGHT));
                }
                else if (hasAddRowArrow(cellPosition))
                {
                    FXUtility.setPseudoclass(item, "expand-right-header", false);
                    FXUtility.setPseudoclass(item, "expand-right", false);
                    FXUtility.setPseudoclass(item, "expand-down", true);
                    item.setTooltip(new Tooltip("Click to add row"));
                    item.setVisible(true);
                    item.setText(DOWN_ARROW);
                    item.setOnAction(e -> {
                        Workers.onWorkerThread("Adding row", Priority.SAVE, () -> {
                            @Nullable AppendRows appendOp = tableDisplay.getTable().getOperations().appendRows;
                            if (appendOp != null)
                                appendOp.appendRows(1);
                        });
                    });
                    buttonTableDisplaysAndHoverStyles.put(item, new Pair<>(tableDisplay, CellStyle.HOVERING_EXPAND_DOWN));
                }
                else
                {
                    Log.error("Table arrow button found but not for column or for row! "
                        + "Position: " + cellPosition
                        + "Data: " + tableDisplay.getDataDisplayTopLeftIncl() + " to " + tableDisplay.getDataDisplayBottomRightIncl());
                    // Panic -- hide it:
                    item.setVisible(false);
                }
            }
            
            @Override
            public ObjectExpression<? extends Collection<CellStyle>> styleForAllCells()
            {
                return tableDisplay.styleForAllCells();
            }
        });
    }

    @Override
    protected void keyboardActivate(CellPosition cellPosition)
    {
        Button button = getItemAt(cellPosition);
        if (button != null)
            button.fire();
    }
}
