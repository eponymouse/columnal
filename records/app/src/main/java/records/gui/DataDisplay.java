package records.gui;

import annotation.units.AbsColIndex;
import annotation.units.GridAreaRowIndex;
import annotation.units.TableDataColIndex;
import annotation.units.TableDataRowIndex;
import com.google.common.collect.ImmutableList;
import javafx.beans.binding.DoubleExpression;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Rectangle;
import log.Log;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.CellPosition;
import records.data.ColumnId;
import records.data.TableId;
import records.data.TableManager;
import records.data.TableOperations;
import records.data.datatype.DataType;
import records.gui.DataCellSupplier.CellStyle;
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
import records.gui.stf.EditorKitSimpleLabel;
import records.gui.stf.StructuredTextField;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FXPlatformConsumer;
import utility.FXPlatformFunction;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;
import utility.gui.TranslationUtility;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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
    // One for table title, one for column names, one for column types:
    public static final int HEADER_ROWS = 3;
    
    protected final VirtualGridSupplierFloating floatingItems;
    private final List<FloatingItem> columnHeaderItems = new ArrayList<>();
    private @Nullable TableHeaderItem tableHeaderItem;
    private TableId curTableId;

    protected @TableDataRowIndex int currentKnownRows; 
    
    // Not final because it may changes if user changes the display item or preview options change:
    @OnThread(Tag.FXPlatform)
    protected ImmutableList<ColumnDetails> displayColumns = ImmutableList.of();

    protected final SimpleObjectProperty<ImmutableList<CellStyle>> cellStyles = new SimpleObjectProperty<>(ImmutableList.of());

    protected final RectangularTableCellSelection.TableSelectionLimits dataSelectionLimits;
    
    // Version without table header
    protected DataDisplay(TableId initialTableName, VirtualGridSupplierFloating floatingItems)
    {
        this.curTableId = initialTableName;
        this.floatingItems = floatingItems;

        this.dataSelectionLimits = new TableSelectionLimits()
        {
            @Override
            public CellPosition getTopLeftIncl()
            {
                return getDataDisplayTopLeftIncl().from(getPosition());
            }

            @Override
            public CellPosition getBottomRightIncl()
            {
                return getDataDisplayBottomRightIncl().from(getPosition());
            }

            @Override
            public void doCopy(CellPosition topLeftIncl, CellPosition bottomRightIncl)
            {
                DataDisplay.this.doCopy(new RectangleBounds(topLeftIncl, bottomRightIncl));
            }
        };
    }

    // Version with table header
    protected DataDisplay(TableManager tableManager, TableId initialTableName, @Nullable FXPlatformConsumer<TableId> renameTable, VirtualGridSupplierFloating floatingItems)
    {
        this(initialTableName, floatingItems);
        tableHeaderItem = new TableHeaderItem(tableManager, initialTableName, renameTable, floatingItems);
        this.floatingItems.addItem(tableHeaderItem);
    }

    protected @Nullable ContextMenu getTableHeaderContextMenu()
    {
        return null;
    }

    @OnThread(Tag.FXPlatform)
    public void setColumns(@UnknownInitialization(DataDisplay.class) DataDisplay this, ImmutableList<ColumnDetails> columns, @Nullable TableOperations operations, @Nullable FXPlatformFunction<ColumnId, ImmutableList<ColumnOperation>> columnActions)
    {
        // Remove old columns:
        columnHeaderItems.forEach(floatingItems::removeItem);
        columnHeaderItems.clear();
        this.displayColumns = columns;        
        
        for (int i = 0; i < columns.size(); i++)
        {
            final int columnIndex = i;
            ColumnDetails column = columns.get(i);
            // Item for column name:
            FloatingItem<Pane> columnNameItem = new FloatingItem<Pane>(ViewOrder.FLOATING)
            {
                private double containerTranslateY;
                private double y;
                private double lastY;

                @OnThread(Tag.FXPlatform)
                private CellPosition getFloatingPosition()
                {
                    return new CellPosition(getPosition().rowIndex + CellPosition.row(1), getPosition().columnIndex + CellPosition.col(columnIndex));
                }
                
                @Override
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
                    y = visibleBounds.getYCoord(pos.rowIndex);
                    double width = visibleBounds.getXCoordAfter(pos.columnIndex) - x;
                    double height = visibleBounds.getYCoordAfter(pos.rowIndex) - y;

                    lastY = visibleBounds.getYCoord(getDataDisplayBottomRightIncl().from(getPosition()).rowIndex - CellPosition.row(1));
                    
                    // So, y and lastY are in the VirtualGrid's coordinate space.  We know that 0 is logically the top
                    // in the render coordinates we have been given (even if VirtualGrid happens to be rendering more
                    // above that).
                    // y is our theoretical placing, if there was no pinning.  lastY is the last (highest Y coordinate) Y
                    // that we could place when we are pinned.  Therefore, our placement will always be between y and lastY.
                    
                    // We also need to plan for the possible translate effect which occurs when virtual grid is smooth scrolling.
                    // The translate Y on the virtual grid will be positive when scrolling the viewport down, because everything has been drawn
                    // in the right spot, then translated down to look like it hasn't moved yet, beforeslowly regressing to zero.
                    // Similarly, translate Y is negative when scrolling viewport up because everything has been translated up
                    // but the translate regresses to zero.
                    
                    // We need to fight this translation because by default, we'll pin to where we should be at the end of the translation,
                    // but while the translate is non-zero, we will be in the wrong spot.  Not correct when we are not meant
                    // to scroll while pinned.  So we set our own translate property that ideally would be the negative of
                    // the container's translate Y to counter-act it.  The confusing thing is that we need to not permanently counter-act
                    // it, otherwise when the smooth scrolling trips the pinned/unpinned boundary, we'll get weird behaviour.
                    // So we set limits on the lowest and highest translate Y that we will accept.
                    
                    // If we are unpinned at the top, the highest translate Y is unbounded because we can scroll all the way down.
                    // The lowest is the negation of our Y position.
                    
                    // If we are pinned, the lowest translate Y is the negation of our Y position.  The maximum is the
                    // negation of the last Y.
                    
                    double layoutY;

                    // If lastY is off the screen, we should be exactly there:
                    if (lastY < 0)
                    {
                        // The pinned header must scroll up out of view:
                        layoutY = lastY;
                    }
                    // If y is off the screen (but lastY is not), we should be at the top, pinned:
                    else if (y < 0)
                    {
                        // Pinned within view:
                        layoutY = 0;
                        
                    }
                    // Otherwise, both y and lastY are on screen, so we should just be at Y
                    else
                    {
                        layoutY = y;
                    }
                    
                    updateTranslate(getNode());
                    
                    return Optional.of(new BoundingBox(
                            x,
                            layoutY,
                            width,
                            height
                    ));
                }

                @Override
                @OnThread(Tag.FXPlatform)
                public Pane makeCell(VisibleBounds visibleBounds)
                {
                    ColumnNameTextField textField = new ColumnNameTextField(column.getColumnId());
                    textField.sizeToFit(30.0, 30.0);
                    @Nullable FXPlatformConsumer<ColumnId> renameColumn = column.getRenameColumn();
                    if (renameColumn == null)
                        textField.setEditable(false);
                    else
                    {
                        @NonNull FXPlatformConsumer<ColumnId> renameColumnFinal = renameColumn;
                        textField.addOnFocusLoss(newColumnId -> {
                            if (newColumnId != null)
                                renameColumnFinal.consume(newColumnId);
                        });
                    }

                    final BorderPane borderPane = new BorderPane(textField.getNode());
                    borderPane.getStyleClass().add("table-display-column-title");
                    BorderPane.setAlignment(textField.getNode(), Pos.CENTER_LEFT);
                    BorderPane.setMargin(textField.getNode(), new Insets(0, 0, 0, 2));
                    FXUtility.addChangeListenerPlatformNN(cellStyles, cellStyles -> {
                        for (CellStyle style : CellStyle.values())
                        {
                            style.applyStyle(borderPane, cellStyles.contains(style));
                        }
                    });

                    ContextMenu contextMenu = new ContextMenu();
                    if (columnActions != null)
                        contextMenu.getItems().setAll(Utility.mapList(columnActions.apply(column.getColumnId()), c -> c.makeMenuItem()));
                    if (contextMenu.getItems().isEmpty())
                    {
                        MenuItem menuItem = new MenuItem("No operations");
                        menuItem.setDisable(true);
                        contextMenu.getItems().setAll(menuItem);
                    }
                    borderPane.setOnContextMenuRequested(e -> contextMenu.show(borderPane, e.getScreenX(), e.getScreenY()));
                    textField.setContextMenu(contextMenu);
                    
                    return borderPane;
                }

                @Override
                public void adjustForContainerTranslation(Pane node, Pair<DoubleExpression, DoubleExpression> translateXY)
                {
                    FXUtility.addChangeListenerPlatformNN(translateXY.getSecond(), ty -> {
                        containerTranslateY = ty.doubleValue();
                        updateTranslate(node);
                    });
                }
                
                @OnThread(Tag.FX)
                private void updateTranslate(@Nullable Pane borderPane)
                {
                    if (borderPane != null)
                    {
                        if (lastY < 0)
                        {
                            // We should be off the screen.  But if containerTranslateY
                            // would keep us on screen, we must act like we are pinned
                            if (containerTranslateY > -lastY)
                                borderPane.setTranslateY(-lastY - containerTranslateY);
                            else
                                borderPane.setTranslateY(0.0);
                        }
                        else if (y < 0)
                        {
                            // Pinned.  Our layoutY is zero
                            
                            // If our translate would make us pin to above the top position
                            // don't let that happen and just let us be translated:
                            if (-containerTranslateY < y)
                                borderPane.setTranslateY(y);
                            else if (-containerTranslateY > lastY)
                                borderPane.setTranslateY(lastY);
                            else
                                borderPane.setTranslateY(-containerTranslateY);
                        }
                        else
                        {
                            // We should be scrolling around, as we are not yet pinned.
                            // But if the containerTranslateY is less than negative y, we need
                            // to clamp to prevent ourselves being off-screen when we should appear temporarily pinned
                            if (containerTranslateY < -y)
                                borderPane.setTranslateY(-y - containerTranslateY);
                            else
                                borderPane.setTranslateY(0.0);
                        }
                    }
                }
            };
            // Item for column type:
            FloatingItem<Label> columnTypeItem = new FloatingItem<Label>(ViewOrder.STANDARD_CELLS)
            {
                @OnThread(Tag.FXPlatform)
                private CellPosition getFloatingPosition()
                {
                    return new CellPosition(getPosition().rowIndex + CellPosition.row(2), getPosition().columnIndex + CellPosition.col(columnIndex));
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
                    typeLabel.getStyleClass().add("table-display-column-title");
                    return typeLabel;
                }
            };
            
            columnHeaderItems.add(columnNameItem);
            columnHeaderItems.add(columnTypeItem);
            floatingItems.addItem(columnNameItem);
            floatingItems.addItem(columnTypeItem);
        }
        
        updateParent();
        
    }

    public void addCellStyle(CellStyle cellStyle)
    {
        cellStyles.set(Utility.consList(cellStyle, cellStyles.get()));
    }

    public void removeCellStyle(CellStyle cellStyle)
    {
        cellStyles.set(cellStyles.get().stream().filter(s -> !s.equals(cellStyle)).collect(ImmutableList.toImmutableList()));
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
        return gridRowIndex - HEADER_ROWS;
    }

    @Override
    protected CellPosition recalculateBottomRightIncl()
    {
        return getPosition().offsetByRowCols(currentKnownRows + (displayColumns.isEmpty() ? 0 : HEADER_ROWS) - 1, Math.max(0, displayColumns.size() - 1));
    }

    // The top left data item in grid area terms, not including any headers
    @SuppressWarnings("units")
    @OnThread(Tag.FXPlatform)
    public final GridAreaCellPosition getDataDisplayTopLeftIncl(@UnknownInitialization(GridArea.class) DataDisplay this)
    {
        return new GridAreaCellPosition(HEADER_ROWS, 0);
    }

    // The last data row in grid area terms, not including any append buttons
    @SuppressWarnings("units")
    @OnThread(Tag.FXPlatform)
    public final GridAreaCellPosition getDataDisplayBottomRightIncl(@UnknownInitialization(GridArea.class) DataDisplay this)
    {
        return new GridAreaCellPosition(HEADER_ROWS + currentKnownRows - 1, displayColumns == null ? 0 : (displayColumns.size() - 1));
    }

    public GridCellInfo<StructuredTextField, CellStyle> getDataGridCellInfo()
    {
        return new GridCellInfo<StructuredTextField, CellStyle>()
        {
            @Override
            public @Nullable GridAreaCellPosition cellAt(CellPosition cellPosition)
            {
                int tableDataRow = cellPosition.rowIndex - (getPosition().rowIndex + HEADER_ROWS);
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
            public void fetchFor(GridAreaCellPosition cellPosition, FXPlatformFunction<CellPosition, @Nullable StructuredTextField> getCell)
            {
                // Blank then queue fetch:
                StructuredTextField orig = getCell.apply(cellPosition.from(getPosition()));
                if (orig != null)
                    orig.resetContent(new EditorKitSimpleLabel<>(TranslationUtility.getString("data.loading")));
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
                        (rowIndex, colIndex, editorKit) -> {
                            // The rowIndex and colIndex are in table data terms, so we must translate:
                            @Nullable StructuredTextField cell = getCell.apply(cellPosition.from(getPosition()));
                            if (cell != null)// && cell == orig)
                                cell.resetContent(editorKit);
                        }
                    );
                }
            }

            @Override
            public boolean checkCellUpToDate(GridAreaCellPosition cellPosition, StructuredTextField cellFirst)
            {
                // If we are for the right position, we haven't been scrolled out of view.
                // If the table is re-run, we'll get reset separately, so we are always up to date:
                return true;
            }

            @Override
            public ObjectExpression<? extends Collection<CellStyle>> styleForAllCells()
            {
                return cellStyles;
            }
        };
    }

    public void cleanupFloatingItems()
    {
        for (FloatingItem columnHeaderItem : columnHeaderItems)
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
        else if (cellPosition.rowIndex >= us.rowIndex + HEADER_ROWS)
        {
            return new RectangularTableCellSelection(cellPosition.rowIndex, cellPosition.columnIndex, dataSelectionLimits);
        }
        // Column name or expand arrow
        return new SingleCellSelection(cellPosition);
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
            return Optional.of(Either.left(new BoundingBox(topLeft.getX(), topLeft.getY(), width, height)));
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
            Optional<CellPosition> pos = visibleBounds.getNearestTopLeftToScreenPos(lastMousePosScreen.subtract(offsetFromTopLeftOfSource));
            if (pos.isPresent())
            {
                destPosition = pos.get(); 
                return Optional.of(Either.right(new RectangleBounds(
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
        public void doCopy()
        {
            DataDisplay.this.doCopy(new RectangleBounds(pos, pos));
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
            tableNameField = new TableNameTextField(tableManager, initialTableName);
            tableNameField.sizeToFit(30.0, 30.0);
            if (renameTable == null)
            {
                tableNameField.setEditable(false);
            }
            else
            {
                @NonNull FXPlatformConsumer<TableId> renameTableFinal = renameTable;
                tableNameField.addOnFocusLoss(newTableId -> {
                    if (newTableId != null)
                        renameTableFinal.consume(newTableId);

                    @Nullable TableId newVal = tableNameField == null ? null : tableNameField.valueProperty().getValue();
                    if (newVal != null)
                      curTableId = newVal;
                });
            }
            final BorderPane borderPane = new BorderPane(tableNameField.getNode());
            borderPane.getStyleClass().add("table-display-table-title");
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
                        FXUtility.mouse(DataDisplay.this).cellStyles.get().stream().filter(s -> s != CellStyle.TABLE_DRAG_SOURCE).collect(ImmutableList.toImmutableList())
                    );
                    CellStyle.TABLE_DRAG_SOURCE.applyStyle(borderPane, false);
                    withParent_(p -> FXUtility.mouse(DataDisplay.this).setPosition(dest));
                    FXUtility.mouse(DataDisplay.this).updateParent();
                    overlays.clear();
                }
                e.consume();
            });
            borderPane.setOnMouseClicked(e -> {
                withParent_(p -> {
                    @AbsColIndex int columnIndex = p.getVisibleBounds().getNearestTopLeftToScreenPos(new Point2D(e.getScreenX(), e.getScreenY())).orElse(getPosition()).columnIndex;
                    p.select(new EntireTableSelection(DataDisplay.this, columnIndex));
                });
                e.consume();
            });
            borderPane.setOnContextMenuRequested(e -> {
                @Nullable ContextMenu contextMenu = FXUtility.mouse(DataDisplay.this).getTableHeaderContextMenu();
                if (contextMenu != null)
                    contextMenu.show(borderPane, e.getScreenX(), e.getScreenY());
            });
            return borderPane;
        }

        @OnThread(Tag.FXPlatform)
        public void setSelected(boolean selected)
        {
            if (getNode() != null)
                FXUtility.setPseudoclass(getNode(), "table-selected", selected);
        }
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public Pair<VirtualGrid.ListenerOutcome, @Nullable FXPlatformConsumer<VisibleBounds>> selectionChanged(@Nullable CellSelection oldSelection, @Nullable CellSelection newSelection)
    {
        if (tableHeaderItem != null)
            tableHeaderItem.setSelected(newSelection instanceof EntireTableSelection && newSelection.includes(this));
        return new Pair<>(ListenerOutcome.KEEP, null);
    }
}
