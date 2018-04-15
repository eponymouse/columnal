package records.gui;

import javafx.beans.binding.ObjectExpression;
import javafx.geometry.Point2D;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import log.Log;
import org.checkerframework.checker.initialization.qual.Initialized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.CellPosition;
import records.data.ColumnId;
import records.data.TableManager;
import records.data.TableOperations;
import records.data.TableOperations.AppendRows;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.gui.DataCellSupplier.CellStyle;
import records.gui.EditColumnDialog.ColumnDetails;
import records.gui.grid.GridAreaCellPosition;
import records.gui.grid.VirtualGridSupplierIndividual;
import records.gui.grid.VirtualGridSupplierIndividual.GridCellInfo;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformFunction;
import utility.Pair;
import utility.Utility;
import utility.Workers;
import utility.Workers.Priority;
import utility.gui.FXUtility;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.WeakHashMap;

@OnThread(Tag.FXPlatform)
public class ExpandTableArrowSupplier extends VirtualGridSupplierIndividual<Button, CellStyle, GridCellInfo<Button, CellStyle>>
{
    public static final String RIGHT_ARROW = "\u27F6";
    public static final String DOWN_ARROW = "\u2193";
    private WeakHashMap<Button, Pair<TableDisplay, CellStyle>> buttonTableDisplaysAndHoverStyles = new WeakHashMap<>();
    private final View parent;
    
    public ExpandTableArrowSupplier(View parent)
    {
        super(ViewOrder.STANDARD_CELLS, Arrays.asList(CellStyle.values()));
        this.parent = parent;
    }
    
    @Override
    protected Button makeNewItem()
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
    protected ItemState getItemState(Button item, Point2D screenPos)
    {
        return ItemState.DIRECTLY_CLICKABLE;
    }

    public void addTable(TableDisplay tableDisplay)
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

            @OnThread(Tag.FXPlatform)
            private boolean hasAddRowArrow(GridAreaCellPosition cellPosition)
            {
                return tableDisplay.getTable().getOperations().appendRows != null
                    && cellPosition.rowIndex == tableDisplay.getDataDisplayBottomRightIncl().rowIndex + 1
                    && cellPosition.columnIndex >= tableDisplay.getDataDisplayTopLeftIncl().columnIndex
                    && cellPosition.columnIndex <= tableDisplay.getDataDisplayBottomRightIncl().columnIndex;
            }

            @OnThread(Tag.FXPlatform)
            public boolean hasAddColumnArrow(GridAreaCellPosition cellPosition)
            {
                return tableDisplay.getTable().getOperations().addColumn != null
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
            public void fetchFor(GridAreaCellPosition cellPosition, FXPlatformFunction<CellPosition, @Nullable Button> getCell)
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
                        Optional<ColumnDetails> optInitialDetails = new EditColumnDialog(parent.getWindow(), parent.getManager(), tableDisplay.getTable().proposeNewColumnName()).showAndWait();
                        optInitialDetails.ifPresent(initialDetails -> {
                            Workers.onWorkerThread("Adding column", Priority.SAVE_ENTRY, () -> {
                                TableOperations.@Nullable AddColumn appendOp = tableDisplay.getTable().getOperations().addColumn;
                                if (appendOp != null)
                                    appendOp.addColumn(null, initialDetails.columnId, initialDetails.dataType, initialDetails.defaultValue);
                            });
                        });
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
                        Workers.onWorkerThread("Adding row", Priority.SAVE_ENTRY, () -> {
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
}
