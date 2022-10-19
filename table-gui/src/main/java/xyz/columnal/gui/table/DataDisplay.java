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

package xyz.columnal.gui.table;

import annotation.units.AbsColIndex;
import annotation.units.AbsRowIndex;
import annotation.units.GridAreaColIndex;
import annotation.units.GridAreaRowIndex;
import annotation.units.TableDataColIndex;
import annotation.units.TableDataRowIndex;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import javafx.beans.binding.DoubleExpression;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.geometry.BoundingBox;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.shape.Rectangle;
import javafx.stage.Modality;
import javafx.stage.Window;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import xyz.columnal.data.CellPosition;
import xyz.columnal.id.ColumnId;
import xyz.columnal.id.DataItemPosition;
import xyz.columnal.data.Table;
import xyz.columnal.id.TableId;
import xyz.columnal.data.TableManager;
import xyz.columnal.data.TableOperations;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.gui.table.DataCellSupplier.CellStyle;
import xyz.columnal.gui.table.DataCellSupplier.VersionedSTF;
import xyz.columnal.gui.table.DataDisplay.ColumnHeaderOps.EditTarget;
import xyz.columnal.utility.gui.ErrorableTextField;
import xyz.columnal.utility.gui.ErrorableTextField.ConversionResult;
import xyz.columnal.gui.dtf.ReadOnlyDocument;
import xyz.columnal.gui.dtf.RecogniserDocument;
import xyz.columnal.gui.grid.CellSelection;
import xyz.columnal.gui.grid.GridArea;
import xyz.columnal.gui.grid.GridAreaCellPosition;
import xyz.columnal.gui.grid.RectangleBounds;
import xyz.columnal.gui.grid.RectangularTableCellSelection;
import xyz.columnal.gui.grid.RectangularTableCellSelection.TableSelectionLimits;
import xyz.columnal.gui.grid.VirtualGridSupplier.ItemState;
import xyz.columnal.gui.grid.VirtualGridSupplier.ViewOrder;
import xyz.columnal.gui.grid.VirtualGridSupplier.VisibleBounds;
import xyz.columnal.gui.grid.VirtualGridSupplierFloating;
import xyz.columnal.gui.grid.VirtualGridSupplierFloating.FloatingItem;
import xyz.columnal.gui.grid.VirtualGridSupplierIndividual.GridCellInfo;
import xyz.columnal.gui.stable.ColumnDetails;
import xyz.columnal.gui.stable.ColumnOperation;
import xyz.columnal.styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.fx.FXPlatformConsumer;
import xyz.columnal.utility.function.fx.FXPlatformFunction;
import xyz.columnal.utility.function.fx.FXPlatformRunnable;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.gui.FXUtility;
import xyz.columnal.utility.gui.GUI;
import xyz.columnal.utility.TranslationUtility;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * A DataDisplay is a GridArea that can be used to display some table data.  Crucially, it does
 * NOT have to be a RecordSet.  This class is used directly for showing import preview, and is
 * used as the base class for {@link TableDisplay.TableDataDisplay} which does show data from a real
 * RecordSet.
 */
@OnThread(Tag.FXPlatform)
public abstract class DataDisplay extends HeadedDisplay
{    
    protected final VirtualGridSupplierFloating floatingItems;
    private final List<FloatingItem<Label>> columnHeaderItems = new ArrayList<>();

    protected @TableDataRowIndex int currentKnownRows; 
    
    // Not final because it may changes if user changes the display item or preview options change:
    // Also note that this is used by reference as an up-to-date check in GridCellInfo
    @OnThread(Tag.FXPlatform)
    protected ImmutableList<ColumnDetails> displayColumns = ImmutableList.of();
    // This is re-assigned all at once when columns change:
    protected ImmutableMap<ColumnId, ColumnHeaderOps> columnHeaderOps = ImmutableMap.of();

    protected final SimpleObjectProperty<ImmutableList<CellStyle>> cellStyles = new SimpleObjectProperty<>(ImmutableList.of());

    protected final RectangularTableCellSelection.TableSelectionLimits dataSelectionLimits;
    private final HeaderRows headerRows;

    private DataDisplay(@Nullable TableHeaderItemParams tableHeaderItemParams, VirtualGridSupplierFloating floatingItems, HeaderRows headerRows)
    {
        super(tableHeaderItemParams, floatingItems);
        this.floatingItems = floatingItems;
        this.headerRows = headerRows;

        this.dataSelectionLimits = new TableSelectionLimits()
        {
            @Override
            public CellPosition getTopLeftIncl()
            {
                return Utility.later(DataDisplay.this).getDataDisplayTopLeftIncl().from(getPosition());
            }

            @Override
            public CellPosition getBottomRightIncl()
            {
                return Utility.later(DataDisplay.this).getDataDisplayBottomRightIncl().from(getPosition());
            }

            @Override
            public void doCopy(CellPosition topLeftIncl, CellPosition bottomRightIncl)
            {
                Utility.later(DataDisplay.this).doCopy(new RectangleBounds(topLeftIncl, bottomRightIncl));
            }

            @Override
            public void doDelete(CellPosition topLeftIncl, CellPosition bottomRightIncl)
            {
                for (@AbsColIndex int colIndex = topLeftIncl.columnIndex; colIndex <= bottomRightIncl.columnIndex; colIndex++)
                {
                    ColumnId columnId = displayColumns.get(colIndex - getPosition().columnIndex).getColumnId();
                    for (@AbsRowIndex int row = topLeftIncl.rowIndex; row <= bottomRightIncl.rowIndex; row++)
                    {
                        Utility.later(DataDisplay.this).deleteValue(columnId, Utility.later(DataDisplay.this).getTableRowIndexFromAbsRow(row));
                    }
                }
            }

            @Override
            public void gotoRow(Window parent, @AbsColIndex int column)
            {
                Utility.later(DataDisplay.this).gotoRow(parent, column);
            }
        };
    }

    // Version without table header
    protected DataDisplay(TableId initialTableName, VirtualGridSupplierFloating floatingItems, boolean showColumnNames, boolean showColumnTypes)
    {
        this(null, floatingItems, new HeaderRows(false, showColumnNames, showColumnTypes));
    }

    // Version with full table header
    protected DataDisplay(TableManager tableManager, Table table, VirtualGridSupplierFloating floatingItems)
    {
        this(new TableHeaderItemParams(tableManager, table.getId(), table, floatingItems), floatingItems, new HeaderRows(true, true, true));
    }

    @OnThread(Tag.FXPlatform)
    public void setColumns(@UnknownInitialization(DataDisplay.class) DataDisplay this, ImmutableList<ColumnDetails> columns, @Nullable TableOperations operations, @Nullable FXPlatformFunction<ColumnId, ColumnHeaderOps> columnActions)
    {
        // Remove old columns:
        columnHeaderItems.forEach(x -> floatingItems.removeItem(x));
        columnHeaderItems.clear();
        this.displayColumns = columns;
        
        ImmutableMap.Builder<ColumnId, ColumnHeaderOps> colOps = ImmutableMap.builderWithExpectedSize(columns.size());
        
        for (int i = 0; i < columns.size(); i++)
        {
            final int columnIndex = i;
            ColumnDetails column = columns.get(i);
            @Nullable ColumnHeaderOps ops = columnActions == null ? null : columnActions.apply(column.getColumnId());
            if (ops != null)
                colOps.put(column.getColumnId(), ops);
            // Item for column name:
            if (headerRows.showingColumnNameRow)
            {
                FloatingItem<Label> columnNameItem = new ColumnNameItem(columnIndex, column, ops);
                columnHeaderItems.add(columnNameItem);
                floatingItems.addItem(columnNameItem);
            }
            // Item for column type:
            if (headerRows.showingColumnTypeRow)
            {
                FloatingItem<Label> columnTypeItem = new ColumnTypeItem(columnIndex, column, ops);
                columnHeaderItems.add(columnTypeItem);
                floatingItems.addItem(columnTypeItem);
            }
        }
        this.columnHeaderOps = colOps.build();
        
        updateParent();
        
    }

    // Gets the column and the bounds for that column, if one is there (otherwise null is returned)
    public @Nullable Pair<ColumnId, RectangleBounds> getColumnAt(CellPosition cell)
    {
        @AbsRowIndex int topDataRow = getPosition().rowIndex + CellPosition.row(tableHeaderItem == null ? 0 : 1);
        @AbsRowIndex int bottomDataRow = getDataDisplayBottomRightIncl().from(getPosition()).rowIndex;
        if (cell.rowIndex < topDataRow
            || cell.rowIndex > bottomDataRow)
            return null;
        
        int colIndex = cell.columnIndex - getPosition().columnIndex;
        if (0 <= colIndex && colIndex < displayColumns.size())
            return new Pair<>(displayColumns.get(colIndex).getColumnId(), new RectangleBounds(new CellPosition(topDataRow, cell.columnIndex), new CellPosition(bottomDataRow, cell.columnIndex)));
        else
            return null;
    }

    public void addCellStyle(CellStyle cellStyle)
    {
        cellStyles.set(Utility.prependToList(cellStyle, cellStyles.get()));
    }

    public void removeCellStyle(CellStyle cellStyle)
    {
        cellStyles.set(cellStyles.get().stream().filter(s -> !s.equals(cellStyle)).collect(ImmutableList.<CellStyle>toImmutableList()));
    }

    public ObjectExpression<? extends Collection<CellStyle>> styleForAllCells()
    {
        return cellStyles;
    }

    @SuppressWarnings("units")
    protected @TableDataRowIndex int getRowIndexWithinTable(@GridAreaRowIndex int gridRowIndex)
    {
        return gridRowIndex - getHeaderRowCount();
    }

    @SuppressWarnings("units")
    public @AbsRowIndex int getAbsRowIndexFromTableRow(@TableDataRowIndex int tableDataRowIndex)
    {
        return tableDataRowIndex + getPosition().rowIndex + getHeaderRowCount();
    }

    @SuppressWarnings("units")
    public @TableDataRowIndex int getTableRowIndexFromAbsRow(@AbsRowIndex int absIndex)
    {
        return absIndex - (getPosition().rowIndex + getHeaderRowCount());
    }

    @Override
    protected CellPosition recalculateBottomRightIncl()
    {
        return getPosition().offsetByRowCols(Math.max(0, currentKnownRows + (displayColumns.isEmpty() ? 0 : getHeaderRowCount()) - 1), Math.max(0, displayColumns.size() - 1));
    }

    // The top left data item in grid area terms, not including any headers
    @SuppressWarnings("units")
    @OnThread(Tag.FXPlatform)
    public final GridAreaCellPosition getDataDisplayTopLeftIncl(@UnknownInitialization(DataDisplay.class) DataDisplay this)
    {
        return new GridAreaCellPosition(getHeaderRowCount(), 0);
    }

    // The last data row in grid area terms, not including any append buttons
    @SuppressWarnings("units")
    @OnThread(Tag.FXPlatform)
    public GridAreaCellPosition getDataDisplayBottomRightIncl(@UnknownInitialization(DataDisplay.class) DataDisplay this)
    {
        return new GridAreaCellPosition(getHeaderRowCount() + Math.max(0, currentKnownRows - 1), Math.max(0, displayColumns == null ? 0 : (displayColumns.size() - 1)));
    }

    public GridCellInfo<VersionedSTF, CellStyle> getDataGridCellInfo()
    {
        return new GridCellInfo<VersionedSTF, CellStyle>()
        {
            @Override
            public @Nullable GridAreaCellPosition cellAt(CellPosition cellPosition)
            {
                int tableDataRow = cellPosition.rowIndex - (getPosition().rowIndex + getHeaderRowCount());
                int tableDataColumn = cellPosition.columnIndex - getPosition().columnIndex;
                if (0 <= tableDataRow && tableDataRow < currentKnownRows &&
                    0 <= tableDataColumn && tableDataColumn < displayColumns.size())
                {
                    return GridAreaCellPosition.relativeFrom(cellPosition, getPosition());
                }
                else
                {
                    return null;
                }
            }

            @Override
            public ImmutableList<RectangleBounds> getCellBounds()
            {
                return ImmutableList.of(new RectangleBounds(getPosition().offsetByRowCols(getHeaderRowCount(), 0), getPosition().offsetByRowCols(currentKnownRows + getHeaderRowCount(), displayColumns.size())));
            }

            @Override
            public void fetchFor(GridAreaCellPosition cellPosition, FXPlatformFunction<CellPosition, @Nullable VersionedSTF> getCell, FXPlatformRunnable scheduleStyleTogether)
            {
                // Blank then queue fetch:
                VersionedSTF orig = getCell.apply(cellPosition.from(getPosition()));
                if (orig != null)
                    orig.blank(new ReadOnlyDocument(TranslationUtility.getString("data.loading")));
                @SuppressWarnings("units")
                @TableDataColIndex int columnIndexWithinTable = cellPosition.columnIndex;
                @SuppressWarnings("units")
                @TableDataRowIndex int rowIndexWithinTable = getRowIndexWithinTable(cellPosition.rowIndex);
                if (displayColumns != null && columnIndexWithinTable < displayColumns.size())
                {
                    displayColumns.get(columnIndexWithinTable).getColumnHandler().fetchValue(
                        rowIndexWithinTable,
                        b -> {},
                            (k, c) -> withParent_(p -> p.select(new RectangularTableCellSelection(c.rowIndex + CellPosition.row(k == KeyCode.ENTER ? 1 : 0), c.columnIndex, dataSelectionLimits))),
                        (rowIndex, colIndex, doc) -> {
                            // The rowIndex and colIndex are in table data terms, so we must translate:
                            @Nullable VersionedSTF cell = getCell.apply(cellPosition.from(getPosition()));
                            if (cell != null)// && cell == orig)
                            {
                                if (doc instanceof RecogniserDocument)
                                    ((RecogniserDocument<?>)doc).setOnFocusLost(scheduleStyleTogether);
                                cell.setContent(doc, displayColumns);
                                scheduleStyleTogether.run();
                            }
                        }
                    );
                }
            }

            @Override
            public boolean checkCellUpToDate(GridAreaCellPosition cellPosition, VersionedSTF cellFirst)
            {
                // If we are for the right position, we haven't been scrolled out of view.
                // If the table is re-run, columns will get set, so we are always up to date
                // provided the columns haven't changed since:
                return cellFirst.isUsingColumns(displayColumns);
            }

            @Override
            public ObjectExpression<? extends Collection<CellStyle>> styleForAllCells()
            {
                return cellStyles;
            }

            @Override
            public void styleTogether(Collection<Pair<GridAreaCellPosition, VersionedSTF>> cells)
            {
                HashMap<@GridAreaColIndex Integer, ArrayList<VersionedSTF>> cellsByColumn = new HashMap<>();
                
                for (Pair<GridAreaCellPosition, VersionedSTF> cellInfo : cells)
                {
                    cellsByColumn.computeIfAbsent(cellInfo.getFirst().columnIndex, _v -> new ArrayList<>()).add(cellInfo.getSecond());
                }
                
                cellsByColumn.forEach((columnIndexWithinTable, cellsInColumn) -> {
                    if (displayColumns != null && columnIndexWithinTable < displayColumns.size())
                    {
                        displayColumns.get(columnIndexWithinTable).getColumnHandler().styleTogether(cellsInColumn, withParent(g -> g.getColumnWidth(columnIndexWithinTable + getPosition().columnIndex)).orElse(-1.0));
                    }
                });
            }
        };
    }

    @Override
    public void cleanupFloatingItems(VirtualGridSupplierFloating floatingItems)
    {
        for (FloatingItem<Label> columnHeaderItem : columnHeaderItems)
        {
            floatingItems.removeItem(columnHeaderItem);
        }
        super.cleanupFloatingItems(floatingItems);        
    }

    public @Nullable CellSelection getSelectionForSingleCell(ColumnId columnId, @TableDataRowIndex int rowIndex)
    {
        @SuppressWarnings("units")
        @TableDataColIndex int colIndex = Utility.findFirstIndex(displayColumns, c -> c.getColumnId().equals(columnId)).orElse(-1);
        if (colIndex < 0)
            return null;
        CellPosition pos = getDataPosition(rowIndex, colIndex);
        return getSelectionForSingleCell(pos);
    }

    @Override
    public @Nullable CellSelection getSelectionForSingleCell(CellPosition cellPosition)
    {
        if (!contains(cellPosition))
            return null;
        CellPosition us = getPosition();
        // In header?
        if (cellPosition.rowIndex == us.rowIndex)
        {
            return new EntireTableSelection(this, cellPosition.columnIndex);
        }
        // In data cells?
        else if (cellPosition.rowIndex >= us.rowIndex + getHeaderRowCount() && cellPosition.rowIndex < us.rowIndex + currentKnownRows + getHeaderRowCount() && cellPosition.columnIndex < us.columnIndex + getDisplayColumns().size())
        {
            return new RectangularTableCellSelection(cellPosition.rowIndex, cellPosition.columnIndex, dataSelectionLimits);
        }
        // Column name or expand arrow
        return new SingleCellSelection(cellPosition);
    }

    public int getHeaderRowCount(@UnknownInitialization(DataDisplay.class) DataDisplay this)
    {
        return (headerRows.showingTableNameRow ? 1 : 0)
            + (headerRows.showingColumnNameRow ? 1 : 0)
            + (headerRows.showingColumnTypeRow ? 1 : 0);
    }

    // Make a row label context menu
    public @Nullable ContextMenu makeRowContextMenu(@TableDataRowIndex int row)
    {
        return null;
    }

    public static class HeaderRows
    {
        private final boolean showingTableNameRow;
        private final boolean showingColumnNameRow;
        private final boolean showingColumnTypeRow;

        public HeaderRows(boolean showingTableNameRow, boolean showingColumnNameRow, boolean showingColumnTypeRow)
        {
            this.showingTableNameRow = showingTableNameRow;
            this.showingColumnNameRow = showingColumnNameRow;
            this.showingColumnTypeRow = showingColumnTypeRow;
        }
    }


    /**
     * Selection for single non-data cell like column header or expand column arrow
     */
    private class SingleCellSelection implements CellSelection
    {
        private final CellPosition pos;

        public SingleCellSelection(CellPosition cellPosition)
        {
            this.pos = cellPosition;
        }

        @Override
        public CellSelection atHome(boolean extendSelection)
        {
            return this;
        }

        @Override
        public CellSelection atEnd(boolean extendSelection)
        {
            return this;
        }

        @Override
        public Either<CellPosition, CellSelection> move(boolean extendSelection, int byRows, int byColumns)
        {
            if (!extendSelection)
                return Either.left(pos.offsetByRowCols(byRows, byColumns));
            return Either.right(this);
        }

        @Override
        public @Nullable CellSelection extendTo(CellPosition cellPosition)
        {
            return null;
        }

        @Override
        public CellPosition positionToEnsureInView()
        {
            return pos;
        }

        @Override
        public RectangleBounds getSelectionDisplayRectangle()
        {
            return new RectangleBounds(pos, pos);
        }

        @Override
        public boolean isExactly(CellPosition cellPosition)
        {
            return pos.equals(cellPosition);
        }

        @Override
        public boolean includes(@UnknownInitialization(GridArea.class) GridArea tableDisplay)
        {
            return false;
        }

        @Override
        public void gotoRow(Window parent)
        {
            DataDisplay.this.gotoRow(parent, pos.columnIndex);
        }

        @Override
        public void doCopy()
        {
            DataDisplay.this.doCopy(new RectangleBounds(pos, pos));
        }

        @Override
        public void doPaste()
        {
            // TODO
        }

        @Override
        public void doDelete()
        {
            int relCol = pos.columnIndex - getPosition().columnIndex;
            if (pos.rowIndex == getPosition().rowIndex || pos.rowIndex == getPosition().rowIndex + 1)
            {
                if (relCol >= 0 && relCol < getDisplayColumns().size())
                {
                    ColumnId columnId = getDisplayColumns().get(relCol).getColumnId();
                    @Nullable ColumnHeaderOps ops = columnHeaderOps.get(columnId);
                    if (ops != null && ops.getDeleteOperation() != null)
                        ops.getDeleteOperation().executeFX();
                }
            }
        }

        @Override
        public CellPosition getActivateTarget()
        {
            return pos;
        }

        @Override
        public void notifySelected(boolean selected, boolean animateFlash)
        {
            boolean isDown = canExpandDown() && pos.rowIndex == getPosition().rowIndex + getHeaderRowCount() + currentKnownRows;
            boolean isRight = canExpandRight() && pos.columnIndex == getPosition().columnIndex + getDisplayColumns().size();
            
            if ((isDown || isRight) && !(isDown && isRight))
            {
                if (selected)
                    addCellStyle(isDown ? CellStyle.HOVERING_EXPAND_DOWN : CellStyle.HOVERING_EXPAND_RIGHT);
                else
                    removeCellStyle(isDown ? CellStyle.HOVERING_EXPAND_DOWN : CellStyle.HOVERING_EXPAND_RIGHT);
            }
        }

        // For debugging:
        @Override
        public String toString()
        {
            return "SingleCellSelection{" +
                "pos=" + pos +
                '}';
        }
    }

    protected void deleteValue(ColumnId columnId, @TableDataRowIndex int rowIndex)
    {
        // Do nothing by default
    }

    /**
     * The list of columns currently being displayed, in order
     * of their display (first in list is leftmost column).
     */
    public ImmutableList<ColumnDetails> getDisplayColumns()
    {
        return displayColumns;
    }

    public static interface ColumnHeaderOps
    {
        @OnThread(Tag.FXPlatform)
        public ImmutableList<MenuItem> contextOperations();
        
        @Pure
        public @Nullable ColumnOperation getDeleteOperation();
        
        public static enum EditTarget { EDIT_NAME, EDIT_TYPE }
        
        // Used as the hyperlinked edit operation on column headers, if non-null.
        @Pure
        public @Nullable FXPlatformConsumer<EditTarget> getPrimaryEditOperation(); 
    }

    private void setColumnOperationContextMenu(Label columnHeader, @Nullable ColumnHeaderOps columnActions)
    {
        ContextMenu contextMenu = new ContextMenu();
        if (columnActions != null)
            contextMenu.getItems().setAll(columnActions.contextOperations());
        if (contextMenu.getItems().isEmpty())
        {
            MenuItem menuItem = new MenuItem("No operations");
            menuItem.setDisable(true);
            contextMenu.getItems().setAll(menuItem);
        }
        columnHeader.setContextMenu(contextMenu);
    }

    private class ColumnNameItem extends FloatingItem<Label> implements ChangeListener<Number>
    {
        private final int columnIndex;
        private final ColumnDetails column;
        private final @Nullable ColumnHeaderOps columnActions;
        private double containerTranslateY;
        // minTranslateY is zero; we can't scroll above our current position.
        private double maxTranslateY;

        public ColumnNameItem(int columnIndex, ColumnDetails column, @Nullable ColumnHeaderOps columnActions)
        {
            super(ViewOrder.FLOATING);
            this.columnIndex = columnIndex;
            this.column = column;
            this.columnActions = columnActions;
        }

        @OnThread(Tag.FXPlatform)
        private CellPosition getFloatingPosition()
        {
            return new CellPosition(getPosition().rowIndex + CellPosition.row(headerRows.showingTableNameRow ? 1 : 0), getPosition().columnIndex + CellPosition.col(columnIndex));
        }

        @Override
        @OnThread(Tag.FXPlatform)
        protected OptionalDouble getPrefWidthForItem(@AbsColIndex int columnIndex, Label node)
        {
            if (getPosition().offsetByRowCols(0, this.columnIndex).columnIndex == columnIndex)
            {
                return OptionalDouble.of(node.prefWidth(-1));
            }
            else
                return OptionalDouble.empty();
        }

        @Override
        @OnThread(value = Tag.FXPlatform)
        public @Nullable Pair<ItemState, @Nullable StyledString> getItemState(CellPosition cellPosition, Point2D screenPos)
        {
            return cellPosition.equals(getFloatingPosition()) ? new Pair<>(ItemState.DIRECTLY_CLICKABLE, null) : null;
        }

        @Override
        @OnThread(Tag.FXPlatform)
        public Optional<BoundingBox> calculatePosition(VisibleBounds visibleBounds)
        {
            CellPosition pos = getFloatingPosition();
            double x = visibleBounds.getXCoord(pos.columnIndex);
            double y = visibleBounds.getYCoord(pos.rowIndex);
            double width = visibleBounds.getXCoordAfter(pos.columnIndex) - x;
            double height = visibleBounds.getYCoordAfter(pos.rowIndex) - y;

            // The furthest down we go is to sit just above the last data row of the table:
            maxTranslateY = visibleBounds.getYCoord(getDataDisplayBottomRightIncl().from(getPosition()).rowIndex - CellPosition.row(1)) - y;
                                
            updateTranslate(getNode(), width);
            
            return Optional.of(new BoundingBox(
                    x,
                    y,
                    width,
                    height
            ));
        }

        @Override
        @OnThread(Tag.FXPlatform)
        public Label makeCell(VisibleBounds visibleBounds)
        {
            Label columnName = new Label(column.getDisplayHeaderLabel());
            columnName.getStyleClass().add("column-title");
            GUI.showTooltipWhenAbbrev(columnName);
            if (columnActions != null && columnActions.getPrimaryEditOperation() != null)
            {
                @NonNull FXPlatformConsumer<EditTarget> primaryEditOp = columnActions.getPrimaryEditOperation();
                addEditableStyling(columnName);
                columnName.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
                    if (e.getButton() == MouseButton.PRIMARY)
                    {
                        primaryEditOp.consume(EditTarget.EDIT_NAME);
                        e.consume();
                    }
                });
            }
            
            columnName.getStyleClass().add("table-display-column-title");
            columnName.getStyleClass().addAll(column.getDisplayHeaderClasses());
            //BorderPane.setAlignment(columnName, Pos.CENTER_LEFT);
            //BorderPane.setMargin(columnName, new Insets(0, 0, 0, 2));
            FXUtility.addChangeListenerPlatformNN(cellStyles, cellStyles -> {
                for (CellStyle style : CellStyle.values())
                {
                    style.applyStyle(columnName, cellStyles.contains(style));
                }
            });

            setColumnOperationContextMenu(columnName, columnActions);

            return columnName;
        }

        @Override
        @OnThread(value = Tag.FXPlatform)
        public void keyboardActivate(CellPosition cellPosition)
        {
            if (getFloatingPosition().equals(cellPosition) && columnActions != null && columnActions.getPrimaryEditOperation() != null)
            {
                columnActions.getPrimaryEditOperation().consume(EditTarget.EDIT_NAME);
            }
        }

        @Override
        @OnThread(value = Tag.FXPlatform)
        public void adjustForContainerTranslation(Label node, Pair<DoubleExpression, DoubleExpression> translateXY, boolean adding)
        {
            if (adding)
                translateXY.getSecond().addListener(this);
            else
                translateXY.getSecond().removeListener(this);
        }

        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue)
        {
            containerTranslateY = newValue.doubleValue();
            updateTranslate(getNode(), null);
        }

        @OnThread(Tag.FX)
        private void updateTranslate(@Nullable Label label, @Nullable Double futureWidth)
        {
            if (label != null)
            {
                // We try to translate ourselves to equivalent layout Y of zero, but without moving ourselves upwards, or further down than maxTranslateY:
                label.setTranslateY(Utility.clampIncl(0.0, - (label.getLayoutY() + containerTranslateY), maxTranslateY));
                
                FXUtility.setPseudoclass(label, "column-header-floating", label.getTranslateY() != 0.0);
                
                label.setClip(new Rectangle(0, 0, futureWidth != null ? futureWidth : label.getWidth(), label.getHeight() + 20.0));
            }
        }
    }

    private class ColumnTypeItem extends FloatingItem<Label>
    {
        private final int columnIndex;
        private final ColumnDetails column;
        private final @Nullable FXPlatformConsumer<EditTarget> editOp;
        private final @Nullable ColumnHeaderOps columnActions;

        @OnThread(Tag.FXPlatform)
        public ColumnTypeItem(int columnIndex, ColumnDetails column, @Nullable ColumnHeaderOps columnHeaderOps)
        {
            super(ViewOrder.STANDARD_CELLS);
            this.columnIndex = columnIndex;
            this.column = column;
            this.columnActions = columnHeaderOps;
            this.editOp = columnHeaderOps == null ? null : columnHeaderOps.getPrimaryEditOperation();
        }

        @OnThread(Tag.FXPlatform)
        private CellPosition getFloatingPosition()
        {
            return new CellPosition(getPosition().rowIndex + CellPosition.row((headerRows.showingTableNameRow ? 1 : 0) + (headerRows.showingColumnNameRow ? 1 : 0)), getPosition().columnIndex + CellPosition.col(columnIndex));
        }

        @Override
        @OnThread(Tag.FXPlatform)
        protected OptionalDouble getPrefWidthForItem(@AbsColIndex int columnIndex, Label node)
        {
            if (getPosition().offsetByRowCols(0, this.columnIndex).columnIndex == columnIndex)
            {
                return OptionalDouble.of(node.prefWidth(-1));
            }
            else
                return OptionalDouble.empty();
        }

        @Override
        public @Nullable Pair<ItemState, @Nullable StyledString> getItemState(CellPosition cellPosition, Point2D screenPos)
        {
            return cellPosition.equals(getFloatingPosition()) ? new Pair<>(ItemState.DIRECTLY_CLICKABLE, null) : null;
        }

        @Override
        public Optional<BoundingBox> calculatePosition(VisibleBounds visibleBounds)
        {
            CellPosition pos = getFloatingPosition();
            double x = visibleBounds.getXCoord(pos.columnIndex);
            double y = visibleBounds.getYCoord(pos.rowIndex);
            double width = visibleBounds.getXCoordAfter(pos.columnIndex) - x;
            double height = visibleBounds.getYCoordAfter(pos.rowIndex) - y;

            return Optional.of(new BoundingBox(x, y, width, height));
        }

        @Override
        public Label makeCell(VisibleBounds visibleBounds)
        {
            Label typeLabel = new TypeLabel(new ReadOnlyObjectWrapper<@Nullable DataType>(column.getColumnType()));
            typeLabel.getStyleClass().add("table-display-column-type");
            GUI.showTooltipWhenAbbrev(typeLabel);
            if (editOp != null)
            {
                @Nullable FXPlatformConsumer<EditTarget> editOpFinal = editOp;
                addEditableStyling(typeLabel);
                typeLabel.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
                    if (e.getButton() == MouseButton.PRIMARY)
                    {
                        editOpFinal.consume(EditTarget.EDIT_TYPE);
                        e.consume();
                    }
                });
            }
            setColumnOperationContextMenu(typeLabel, columnActions);
            return typeLabel;
        }

        @Override
        public void keyboardActivate(CellPosition cellPosition)
        {
            if (getFloatingPosition().equals(cellPosition) && editOp != null)
                editOp.consume(EditTarget.EDIT_TYPE);
        }
    }

    protected static void addEditableStyling(Label editable)
    {
        editable.getStyleClass().add("column-title-edit");
    }
    
    @Override
    public void gotoRow(Window parent, @AbsColIndex int currentCol)
    {
        new GotoRowDialog(parent).showAndWait().ifPresent(row -> {
            withParent_(g -> {
                CellPosition p = new CellPosition(getAbsRowIndexFromTableRow(row), currentCol);
                CellSelection selection = getSelectionForSingleCell(p);
                if (selection != null)
                    g.select(selection);
            });
        });
    }

    @Override
    protected void setTableDragSource(boolean on, BorderPane borderPane)
    {
        if (on)
        {
            ImmutableList.Builder<CellStyle> newStyles = ImmutableList.builder();
            newStyles.addAll(cellStyles.get());
            CellStyle blurStyle = CellStyle.TABLE_DRAG_SOURCE;
            newStyles.add(blurStyle);
            cellStyles.set(newStyles.build());
            blurStyle.applyStyle(borderPane, true);
        }
        else
        {
            cellStyles.set(
                cellStyles.get().stream().filter(s -> s != CellStyle.TABLE_DRAG_SOURCE).collect(ImmutableList.<CellStyle>toImmutableList())
            );
            CellStyle.TABLE_DRAG_SOURCE.applyStyle(borderPane, false);
        }
    }
    
    protected boolean canExpandDown()
    {
        // Default implementation:
        return false;
    }

    protected boolean canExpandRight()
    {
        // Default implementation:
        return false;
    }

    @OnThread(Tag.FXPlatform)
    private class GotoRowDialog extends Dialog<@TableDataRowIndex Integer>
    {
        private final ErrorableTextField<@TableDataRowIndex Integer> textField;

        public GotoRowDialog(Window parent)
        {
            initOwner(parent);
            initModality(Modality.WINDOW_MODAL);
            this.textField = new ErrorableTextField<@TableDataRowIndex Integer>(this::parseRowNumber);
            setResultConverter(bt -> {
                if (bt == ButtonType.OK)
                    return textField.valueProperty().get();
                else
                    return null;
            });
            getDialogPane().setPadding(new Insets(10));
            getDialogPane().setContent(textField.getNode());
            getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
            getDialogPane().lookupButton(ButtonType.OK).addEventFilter(ActionEvent.ACTION, e -> {
                if (textField.valueProperty().getValue() == null)
                {
                    e.consume();
                }
            });
            getDialogPane().lookupButton(ButtonType.OK).getStyleClass().add("ok-button");

            setOnShown(e -> {
                FXUtility.runAfter(() -> textField.requestFocusWhenInScene());
            });
        }

        @OnThread(Tag.FXPlatform)
        private ErrorableTextField.ConversionResult<@TableDataRowIndex Integer> parseRowNumber(@UnknownInitialization(Object.class) GotoRowDialog this, String text)
        {
            try
            {
                // User enters one-based, we use zero-based:
                int row = Integer.parseInt(text);
                if (row >= 1 && row <= currentKnownRows)
                    return ConversionResult.success(DataItemPosition.row(row - 1));
                return ConversionResult.error(StyledString.s(TranslationUtility.getString("row.number.invalid") + " (1-" + currentKnownRows + ")"));
            }
            catch (NumberFormatException e)
            {
                return ConversionResult.error(StyledString.s(Utility.concatLocal(TranslationUtility.getString("row.not.a.number"), e.getLocalizedMessage())));
            }
        }
    }
}
