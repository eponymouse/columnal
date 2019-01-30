package records.gui;

import annotation.units.AbsColIndex;
import annotation.units.AbsRowIndex;
import annotation.units.GridAreaColIndex;
import annotation.units.GridAreaRowIndex;
import annotation.units.TableDataColIndex;
import annotation.units.TableDataRowIndex;
import com.google.common.collect.ImmutableList;
import javafx.beans.binding.DoubleExpression;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleObjectProperty;
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
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Rectangle;
import javafx.stage.Modality;
import javafx.stage.Window;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import records.data.CellPosition;
import records.data.ColumnId;
import records.data.DataItemPosition;
import records.data.TableId;
import records.data.TableManager;
import records.data.TableOperations;
import records.data.datatype.DataType;
import records.gui.DataCellSupplier.CellStyle;
import records.gui.DataCellSupplier.VersionedSTF;
import records.gui.dtf.ReadOnlyDocument;
import records.gui.dtf.RecogniserDocument;
import records.gui.grid.CellSelection;
import records.gui.grid.GridArea;
import records.gui.grid.GridAreaCellPosition;
import records.gui.grid.RectangleBounds;
import records.gui.grid.RectangleOverlayItem;
import records.gui.grid.RectangularTableCellSelection;
import records.gui.grid.RectangularTableCellSelection.TableSelectionLimits;
import records.gui.grid.VirtualGrid;
import records.gui.grid.VirtualGrid.ListenerOutcome;
import records.gui.grid.VirtualGrid.SelectionListener;
import records.gui.grid.VirtualGridSupplier.ItemState;
import records.gui.grid.VirtualGridSupplier.ViewOrder;
import records.gui.grid.VirtualGridSupplier.VisibleBounds;
import records.gui.grid.VirtualGridSupplierFloating;
import records.gui.grid.VirtualGridSupplierFloating.FloatingItem;
import records.gui.grid.VirtualGridSupplierIndividual.GridCellInfo;
import records.gui.stable.ColumnDetails;
import records.gui.stable.ColumnOperation;
import records.gui.table.TableDisplay;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FXPlatformConsumer;
import utility.FXPlatformFunction;
import utility.FXPlatformRunnable;
import utility.Pair;
import utility.Utility;
import utility.gui.ErrorableDialog;
import utility.gui.FXUtility;
import utility.gui.TranslationUtility;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A DataDisplay is a GridArea that can be used to display some table data.  Crucially, it does
 * NOT have to be a RecordSet.  This class is used directly for showing import preview, and is
 * used as the base class for {@link TableDisplay.TableDataDisplay} which does show data from a real
 * RecordSet.
 */
@OnThread(Tag.FXPlatform)
public abstract class DataDisplay extends GridArea implements SelectionListener
{    
    protected final VirtualGridSupplierFloating floatingItems;
    private final List<FloatingItem<Label>> columnHeaderItems = new ArrayList<>();
    private @Nullable TableHeaderItem tableHeaderItem;
    private TableId curTableId;

    protected @TableDataRowIndex int currentKnownRows; 
    
    // Not final because it may changes if user changes the display item or preview options change:
    // Also note that this is used by reference as an up-to-date check in GridCellInfo
    @OnThread(Tag.FXPlatform)
    protected ImmutableList<ColumnDetails> displayColumns = ImmutableList.of();

    protected final SimpleObjectProperty<ImmutableList<CellStyle>> cellStyles = new SimpleObjectProperty<>(ImmutableList.of());

    protected final RectangularTableCellSelection.TableSelectionLimits dataSelectionLimits;
    private final HeaderRows headerRows;

    private DataDisplay(TableId initialTableName, VirtualGridSupplierFloating floatingItems, HeaderRows headerRows)
    {
        this.curTableId = initialTableName;
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
                DataDisplay.this.doCopy(new RectangleBounds(topLeftIncl, bottomRightIncl));
            }

            @Override
            public void gotoRow(Window parent, @AbsColIndex int column)
            {
                DataDisplay.this.gotoRow(parent, column);
            }
        };
    }

    // Version without table header
    protected DataDisplay(TableId initialTableName, VirtualGridSupplierFloating floatingItems, boolean showColumnNames, boolean showColumnTypes)
    {
        this(initialTableName, floatingItems, new HeaderRows(false, showColumnNames, showColumnTypes));
    }

                        // Version with full table header
    protected DataDisplay(TableManager tableManager, TableId initialTableName, @Nullable FXPlatformConsumer<TableId> renameTable, VirtualGridSupplierFloating floatingItems)
    {
        this(initialTableName, floatingItems, new HeaderRows(true, true, true));
        tableHeaderItem = new TableHeaderItem(tableManager, initialTableName, renameTable, floatingItems);
        this.floatingItems.addItem(tableHeaderItem);
    }

    protected @Nullable ContextMenu getTableHeaderContextMenu()
    {
        return null;
    }

    @OnThread(Tag.FXPlatform)
    public void setColumns(@UnknownInitialization(DataDisplay.class) DataDisplay this, ImmutableList<ColumnDetails> columns, @Nullable TableOperations operations, @Nullable FXPlatformFunction<ColumnId, ColumnHeaderOps> columnActions)
    {
        // Remove old columns:
        columnHeaderItems.forEach(x -> floatingItems.removeItem(x));
        columnHeaderItems.clear();
        this.displayColumns = columns;        
        
        for (int i = 0; i < columns.size(); i++)
        {
            final int columnIndex = i;
            ColumnDetails column = columns.get(i);
            @Nullable ColumnHeaderOps ops = columnActions == null ? null : columnActions.apply(column.getColumnId());
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

    @Override
    public String getSortKey()
    {
        // At least makes it consistent when ordering jumbled up tables during tests:
        return curTableId.getRaw();
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
        return getPosition().offsetByRowCols(currentKnownRows + (displayColumns.isEmpty() ? 0 : getHeaderRowCount()) - 1, Math.max(0, displayColumns.size() - 1));
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
        return new GridAreaCellPosition(getHeaderRowCount() + currentKnownRows - 1, displayColumns == null ? 0 : (displayColumns.size() - 1));
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
                        c -> withParent_(p -> p.select(new RectangularTableCellSelection(c.rowIndex, c.columnIndex, dataSelectionLimits))),
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

    public void cleanupFloatingItems()
    {
        for (FloatingItem<Label> columnHeaderItem : columnHeaderItems)
        {
            floatingItems.removeItem(columnHeaderItem);
        }
        if (tableHeaderItem != null)
            floatingItems.removeItem(tableHeaderItem);
        
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
        else if (cellPosition.rowIndex >= us.rowIndex + getHeaderRowCount())
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

    public double getTableNameWidth()
    {
        if (tableHeaderItem == null || tableHeaderItem.tableNameField == null)
            return 0;
        else
            return tableHeaderItem.tableNameField.getNode().getLayoutBounds().getWidth();
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
        public CellPosition getActivateTarget()
        {
            return pos;
        }
    }

    /**
     * Copies the given region to clipboard, if possible.
     * 
     * @param dataBounds The bounds of the data region.  May extend beyond the actual data display
     *                   portion.  If null, copy all data, including that which is not loaded yet.
     */
    protected abstract void doCopy(@Nullable RectangleBounds dataBounds);

    private class TableHeaderItem extends FloatingItem<Pane>
    {
        private @Nullable final TableManager tableManager;
        private final TableId initialTableName;
        private @Nullable final FXPlatformConsumer<TableId> renameTable;
        private final VirtualGridSupplierFloating floatingItems;
        private @MonotonicNonNull ErrorableTextField<TableId> tableNameField;
        private @MonotonicNonNull BorderPane borderPane;

        public TableHeaderItem(@Nullable TableManager tableManager, TableId initialTableName, @Nullable FXPlatformConsumer<TableId> renameTable, VirtualGridSupplierFloating floatingItems)
        {
            super(ViewOrder.STANDARD_CELLS);
            this.tableManager = tableManager;
            this.initialTableName = initialTableName;
            this.renameTable = renameTable;
            this.floatingItems = floatingItems;
        }

        @Override
        @OnThread(Tag.FXPlatform)
        public Optional<BoundingBox> calculatePosition(VisibleBounds visibleBounds)
        {
            double x = visibleBounds.getXCoord(getPosition().columnIndex);
            double y = visibleBounds.getYCoord(getPosition().rowIndex);
            double width = visibleBounds.getXCoordAfter(getBottomRightIncl().columnIndex) - x;
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
        public @Nullable ItemState getItemState(CellPosition cellPosition, Point2D screenPos)
        {
            if (cellPosition.rowIndex == getPosition().rowIndex
                && getPosition().columnIndex <= cellPosition.columnIndex && cellPosition.columnIndex <= getBottomRightIncl().columnIndex)
            {
                return tableNameField != null && tableNameField.isFocused() ? ItemState.EDITING : ItemState.DIRECTLY_CLICKABLE;
            }
            return null;
        }

        @Override
        @OnThread(Tag.FXPlatform)
        public Pane makeCell(VisibleBounds visibleBounds)
        {
            tableNameField = new TableNameTextField(tableManager, initialTableName, false, () -> withParent_(g -> g.select(new EntireTableSelection(DataDisplay.this, getPosition().columnIndex))));
            tableNameField.getNode().setFocusTraversable(false);
            tableNameField.sizeToFit(30.0, 30.0);
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
            if (renameTable == null)
            {
                tableNameField.setEditable(false);
            }
            else
            {
                @NonNull FXPlatformConsumer<TableId> renameTableFinal = renameTable;
                tableNameField.addOnFocusLoss(newTableId -> {
                    if (Objects.equals(newTableId, curTableId))
                        return; // Ignore if hasn't actually changed
                    
                    if (newTableId != null)
                        renameTableFinal.consume(newTableId);

                    @Nullable TableId newVal = tableNameField == null ? null : tableNameField.valueProperty().getValue();
                    if (newVal != null)
                      curTableId = newVal;
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
                    ImmutableList.Builder<CellStyle> newStyles = ImmutableList.builder();
                    newStyles.addAll(FXUtility.mouse(DataDisplay.this).cellStyles.get());
                    CellStyle blurStyle = CellStyle.TABLE_DRAG_SOURCE;
                    newStyles.add(blurStyle);
                    FXUtility.mouse(DataDisplay.this).cellStyles.set(newStyles.build());
                    blurStyle.applyStyle(borderPane, true);
                    FXUtility.mouse(DataDisplay.this).updateParent();
                    withParent_(g -> g.setNudgeScroll(true));
                }
                e.consume();
            });
            borderPane.setOnMouseDragged(e -> {
                if (!overlays.isEmpty())
                {
                    overlays.forEach(o -> o.mouseMovedToScreenPos(e.getScreenX(), e.getScreenY()));
                    FXUtility.mouse(DataDisplay.this).updateParent();
                }
                e.consume();
            });
            borderPane.setOnMouseReleased(e -> {
                if (!overlays.isEmpty())
                {
                    CellPosition dest = ((MoveDestinationSnapped)overlays.get(0)).getDestinationPosition();
                    overlays.forEach(o -> floatingItems.removeItem(o));
                    FXUtility.mouse(DataDisplay.this).cellStyles.set(
                        FXUtility.mouse(DataDisplay.this).cellStyles.get().stream().filter(s -> s != CellStyle.TABLE_DRAG_SOURCE).collect(ImmutableList.<CellStyle>toImmutableList())
                    );
                    CellStyle.TABLE_DRAG_SOURCE.applyStyle(borderPane, false);
                    // setPosition calls updateParent()
                    withParent_(p -> {
                        FXUtility.mouse(DataDisplay.this).setPosition(dest);
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
                        p.select(new EntireTableSelection(DataDisplay.this, columnIndex));
                    });
                }
                else if (e.getButton() == MouseButton.MIDDLE && e.isStillSincePress())
                {
                    headerMiddleClicked();
                }
                e.consume();
            });
            borderPane.setOnContextMenuRequested(e -> {
                @Nullable ContextMenu contextMenu = FXUtility.mouse(DataDisplay.this).getTableHeaderContextMenu();
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
                @Nullable ContextMenu contextMenu = FXUtility.mouse(DataDisplay.this).getTableHeaderContextMenu();
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
    }

    // For overridding by subclasses
    protected void headerMiddleClicked()
    {
        
    }
    
    // For overriding in child classes
    protected void tableDraggedToNewPosition()
    {
    }

    protected ImmutableList<String> getExtraTitleStyleClasses()
    {
        return ImmutableList.of();
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public Pair<VirtualGrid.ListenerOutcome, @Nullable FXPlatformConsumer<VisibleBounds>> selectionChanged(@Nullable CellSelection oldSelection, @Nullable CellSelection newSelection)
    {
        if (tableHeaderItem != null)
            tableHeaderItem.setSelected(newSelection instanceof EntireTableSelection && newSelection.includes(this));
        return new Pair<>(ListenerOutcome.KEEP, null);
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
        public ImmutableList<ColumnOperation> contextOperations();
        
        // Used as the hyperlinked edit operation on column headers, if non-null.
        @Pure
        public @Nullable FXPlatformRunnable getPrimaryEditOperation(); 
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
        @OnThread(value = Tag.FXPlatform)
        public @Nullable ItemState getItemState(CellPosition cellPosition, Point2D screenPos)
        {
            return cellPosition.equals(getFloatingPosition()) ? ItemState.DIRECTLY_CLICKABLE : null;
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
                                
            updateTranslate(getNode());
            
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
            Label columnName = new Label(column.getColumnId().getRaw());
            columnName.getStyleClass().add("column-title");
            if (columnActions != null && columnActions.getPrimaryEditOperation() != null)
            {
                @NonNull FXPlatformRunnable primaryEditOp = columnActions.getPrimaryEditOperation();
                addEditableStyling(columnName);
                columnName.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
                    if (e.getButton() == MouseButton.PRIMARY)
                    {
                        primaryEditOp.run();
                        e.consume();
                    }
                });
            }
            
            columnName.getStyleClass().add("table-display-column-title");
            //BorderPane.setAlignment(columnName, Pos.CENTER_LEFT);
            //BorderPane.setMargin(columnName, new Insets(0, 0, 0, 2));
            FXUtility.addChangeListenerPlatformNN(cellStyles, cellStyles -> {
                for (CellStyle style : CellStyle.values())
                {
                    style.applyStyle(columnName, cellStyles.contains(style));
                }
            });

            ContextMenu contextMenu = new ContextMenu();
            if (columnActions != null)
                contextMenu.getItems().setAll(Utility.mapList(columnActions.contextOperations(), c -> c.makeMenuItem()));
            if (contextMenu.getItems().isEmpty())
            {
                MenuItem menuItem = new MenuItem("No operations");
                menuItem.setDisable(true);
                contextMenu.getItems().setAll(menuItem);
            }
            columnName.setOnContextMenuRequested(e -> contextMenu.show(columnName, e.getScreenX(), e.getScreenY()));
            columnName.setContextMenu(contextMenu);
            
            return columnName;
        }

        @Override
        @OnThread(value = Tag.FXPlatform)
        public void keyboardActivate(CellPosition cellPosition)
        {
            if (getFloatingPosition().equals(cellPosition) && columnActions != null && columnActions.getPrimaryEditOperation() != null)
            {
                columnActions.getPrimaryEditOperation().run();
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
            updateTranslate(getNode());
        }

        @OnThread(Tag.FX)
        private void updateTranslate(@Nullable Label label)
        {
            if (label != null)
            {
                // We try to translate ourselves to equivalent layout Y of zero, but without moving ourselves upwards, or further down than maxTranslateY:
                label.setTranslateY(Utility.clampIncl(0.0, - (label.getLayoutY() + containerTranslateY), maxTranslateY));
                
                FXUtility.setPseudoclass(label, "column-header-floating", label.getTranslateY() != 0.0);
                
                label.setClip(new Rectangle(0, 0, label.getWidth(), label.getHeight() + 20.0));
            }
        }
    }

    private class ColumnTypeItem extends FloatingItem<Label>
    {
        private final int columnIndex;
        private final ColumnDetails column;
        private final @Nullable FXPlatformRunnable editOp;

        public ColumnTypeItem(int columnIndex, ColumnDetails column, @Nullable ColumnHeaderOps columnHeaderOps)
        {
            super(ViewOrder.STANDARD_CELLS);
            this.columnIndex = columnIndex;
            this.column = column;
            this.editOp = columnHeaderOps == null ? null : columnHeaderOps.getPrimaryEditOperation();
        }

        @OnThread(Tag.FXPlatform)
        private CellPosition getFloatingPosition()
        {
            return new CellPosition(getPosition().rowIndex + CellPosition.row((headerRows.showingTableNameRow ? 1 : 0) + (headerRows.showingColumnNameRow ? 1 : 0)), getPosition().columnIndex + CellPosition.col(columnIndex));
        }

        @Override
        public @Nullable ItemState getItemState(CellPosition cellPosition, Point2D screenPos)
        {
            return cellPosition.equals(getFloatingPosition()) ? ItemState.DIRECTLY_CLICKABLE : null;
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
            if (editOp != null)
            {
                @NonNull FXPlatformRunnable editOpFinal = editOp;
                addEditableStyling(typeLabel);
                typeLabel.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
                    if (e.getButton() == MouseButton.PRIMARY)
                    {
                        editOpFinal.run();
                        e.consume();
                    }
                });
            }
            return typeLabel;
        }

        @Override
        public void keyboardActivate(CellPosition cellPosition)
        {
            if (getFloatingPosition().equals(cellPosition) && editOp != null)
                editOp.run();
        }
    }

    protected static void addEditableStyling(Label editable)
    {
        editable.getStyleClass().add("column-title-edit");
        Tooltip.install(editable, new Tooltip(TranslationUtility.getString("click.to.change")));
    }
    
    public void gotoRow(Window parent, @AbsColIndex int currentCol)
    {
        new GotoRowDialog(parent).showAndWait().ifPresent(row -> {
            withParent_(g -> {
                CellPosition p = new CellPosition(getAbsRowIndexFromTableRow(row), currentCol);
                g.select(getSelectionForSingleCell(p));
            });
        });
    }

    @OnThread(Tag.FXPlatform)
    private class GotoRowDialog extends ErrorableDialog<@TableDataRowIndex Integer>
    {
        private final TextField textField = new TextField();

        public GotoRowDialog(Window parent)
        {
            initOwner(parent);
            initModality(Modality.WINDOW_MODAL);
            getDialogPane().setContent(textField);

            setOnShown(e -> {
                FXUtility.runAfter(() -> textField.requestFocus());
            });
        }

        @Override
        protected @OnThread(Tag.FXPlatform) Either<@Localized String, @TableDataRowIndex Integer> calculateResult()
        {
            try
            {
                int row = Integer.parseInt(textField.getText());
                if (row >= 0 && row < currentKnownRows)
                    return Either.right(DataItemPosition.row(row));
                return Either.<@Localized String, @TableDataRowIndex Integer>left(TranslationUtility.getString("row.number.invalid"));
            }
            catch (NumberFormatException e)
            {
                return Either.<@Localized String, @TableDataRowIndex Integer>left(Utility.concatLocal(TranslationUtility.getString("row.not.a.number"), e.getLocalizedMessage()));
            }
        }
    }
}
