package records.gui;

import annotation.units.AbsColIndex;
import annotation.units.AbsRowIndex;
import com.google.common.collect.ImmutableList;
import javafx.beans.binding.DoubleExpression;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.BorderPane;
import javafx.scene.shape.Rectangle;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.CellPosition;
import records.data.ColumnId;
import records.data.Table.MessageWhenEmpty;
import records.data.TableId;
import records.data.TableManager;
import records.data.TableOperations;
import records.data.datatype.DataType;
import records.gui.DataCellSupplier.CellStyle;
import records.gui.grid.GridArea;
import records.gui.grid.RectangleBounds;
import records.gui.grid.RectangleOverlayItem;
import records.gui.grid.RectangularTableCellSelection;
import records.gui.grid.RectangularTableCellSelection.TableSelectionLimits;
import records.gui.grid.VirtualGridSupplier.ViewOrder;
import records.gui.grid.VirtualGridSupplier.VisibleDetails;
import records.gui.grid.VirtualGridSupplierFloating;
import records.gui.grid.VirtualGridSupplierFloating.FloatingItem;
import records.gui.stable.ColumnDetails;
import records.gui.stable.ColumnOperation;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.FXPlatformFunction;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;

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
public abstract class DataDisplay extends GridArea
{
    // One for table title, one for column names, one for column types:
    public static final int HEADER_ROWS = 3;
    
    private final VirtualGridSupplierFloating floatingItems;
    private final List<FloatingItem> columnHeaderItems = new ArrayList<>();
    private final FloatingItem tableHeaderItem;
    
    // Not final because it may changes if user changes the display item or preview options change:
    @OnThread(Tag.FXPlatform)
    protected ImmutableList<ColumnDetails> displayColumns = ImmutableList.of();

    protected final SimpleObjectProperty<ImmutableList<CellStyle>> cellStyles = new SimpleObjectProperty<>(ImmutableList.of());

    protected final RectangularTableCellSelection.TableSelectionLimits dataSelectionLimits;
    
    public DataDisplay(@Nullable TableManager tableManager, TableId initialTableName, MessageWhenEmpty messageWhenEmpty, @Nullable FXPlatformConsumer<TableId> renameTable, VirtualGridSupplierFloating floatingItems)
    {
        super(messageWhenEmpty);
        this.floatingItems = floatingItems;
        this.floatingItems.addItem(tableHeaderItem = new FloatingItem() {
              @Override
              @OnThread(Tag.FXPlatform)
              public Optional<BoundingBox> calculatePosition(VisibleDetails<@AbsRowIndex Integer> rowBounds, VisibleDetails<@AbsColIndex Integer> columnBounds)
              {
                  double x = columnBounds.getItemCoord(Utility.boxCol(getPosition().columnIndex));
                  double y = rowBounds.getItemCoord(Utility.boxRow(getPosition().rowIndex));
                  double width = columnBounds.getItemCoord(Utility.boxCol(getPosition().columnIndex + CellPosition.col(getColumnCount()))) - x;
                  double height = rowBounds.getItemCoordAfter(Utility.boxRow(getPosition().rowIndex)) - y;
                  return Optional.of(new BoundingBox(
                      x,
                      y,
                      width,
                      height
                  ));
              }

              @Override
              @OnThread(Tag.FXPlatform)
              public Pair<ViewOrder, Node> makeCell()
              {
                  ErrorableTextField<TableId> tableNameField = new TableNameTextField(tableManager, initialTableName);
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
                      });
                  }
                  BorderPane borderPane = new BorderPane(tableNameField.getNode());
                  borderPane.getStyleClass().add("table-display-table-title");
                  BorderPane.setAlignment(tableNameField.getNode(), Pos.CENTER_LEFT);
                  BorderPane.setMargin(tableNameField.getNode(), new Insets(0, 0, 0, 8.0));
                  borderPane.setOnMouseClicked(e -> {
                      withParent(g -> g.select(new EntireTableSelection(FXUtility.mouse(DataDisplay.this))));
                      FXUtility.setPseudoclass(borderPane, "table-selected", true);
                      withParent(g -> g.onNextSelectionChange(s -> FXUtility.setPseudoclass(borderPane, "table-selected", false)));
                  });
                  @Nullable DestRectangleOverlay overlay[] = new DestRectangleOverlay[1]; 
                  borderPane.setOnMouseDragged(e -> {
                      e.consume();
                      if (overlay[0] != null)
                      {
                          overlay[0].mouseMovedToScreenPos(e.getScreenX(), e.getScreenY());
                          FXUtility.mouse(DataDisplay.this).updateParent();
                          return;
                      }
                      overlay[0] = new DestRectangleOverlay(getPosition(), borderPane.localToScreen(borderPane.getBoundsInLocal()), e.getScreenX(), e.getScreenY());
                      floatingItems.addItem(overlay[0]);
                      ImmutableList.Builder<CellStyle> newStyles = ImmutableList.builder();
                      newStyles.addAll(FXUtility.mouse(DataDisplay.this).cellStyles.get());
                      CellStyle blurStyle = CellStyle.TABLE_DRAG_SOURCE;
                      newStyles.add(blurStyle);
                      FXUtility.mouse(DataDisplay.this).cellStyles.set(newStyles.build());
                      blurStyle.applyStyle(borderPane, true);
                      FXUtility.mouse(DataDisplay.this).updateParent();
                  });
                  borderPane.setOnMouseReleased(e -> {
                      if (overlay[0] != null)
                      {
                          CellPosition dest = overlay[0].getDestinationPosition();
                          floatingItems.removeItem(overlay[0]);
                          FXUtility.mouse(DataDisplay.this).cellStyles.set(
                              FXUtility.mouse(DataDisplay.this).cellStyles.get().stream().filter(s -> s != CellStyle.TABLE_DRAG_SOURCE).collect(ImmutableList.toImmutableList())
                          );
                          CellStyle.TABLE_DRAG_SOURCE.applyStyle(borderPane, false);
                          withParent(p -> FXUtility.mouse(DataDisplay.this).setPosition(dest));
                          FXUtility.mouse(DataDisplay.this).updateParent();
                          overlay[0] = null;
                      }
                      e.consume();
                  });

                  // TODO support dragging to move table
                  return new Pair<>(ViewOrder.FLOATING, borderPane);
              }
          }  
        );

        this.dataSelectionLimits = new TableSelectionLimits()
        {
            //TODO
            @Override
            public @AbsRowIndex int getFirstPossibleRowIncl()
            {
                return CellPosition.row(0);
            }

            @Override
            public @AbsRowIndex int getLastPossibleRowIncl()
            {
                return CellPosition.row(0);
            }

            @Override
            public @AbsColIndex int getFirstPossibleColumnIncl()
            {
                return CellPosition.col(0);
            }

            @Override
            public @AbsColIndex int getLastPossibleColumnIncl()
            {
                return CellPosition.col(0);
            }
        };
    }

    @OnThread(Tag.FXPlatform)
    public void setColumnsAndRows(@UnknownInitialization(DataDisplay.class) DataDisplay this, ImmutableList<ColumnDetails> columns, @Nullable TableOperations operations, @Nullable FXPlatformFunction<ColumnId, ImmutableList<ColumnOperation>> columnActions)
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
            FloatingItem columnNameItem = new FloatingItem()
            {
                // This is like a boolean property, but we store -1 when pinned, 0 when unpinned.
                // That way we negate the parent's translate when pinned, but let it affect us when not:
                DoubleProperty translateFactor = new SimpleDoubleProperty(0);
                DoubleExpression translateX = new ReadOnlyDoubleWrapper(0);
                DoubleExpression translateY = new ReadOnlyDoubleWrapper(0);
                
                @Override
                @OnThread(Tag.FXPlatform)
                public Optional<BoundingBox> calculatePosition(VisibleDetails<@AbsRowIndex Integer> rowBounds, VisibleDetails<@AbsColIndex Integer> columnBounds)
                {
                    @AbsColIndex int calcCol = getPosition().columnIndex + CellPosition.col(columnIndex);
                    @AbsRowIndex int calcRow = getPosition().rowIndex + CellPosition.row(1);
                    double x = columnBounds.getItemCoord(Utility.boxCol(calcCol));
                    double y = rowBounds.getItemCoord(Utility.boxRow(calcRow));
                    double width = columnBounds.getItemCoordAfter(Utility.boxCol(calcCol)) - x;
                    double height = rowBounds.getItemCoordAfter(Utility.boxRow(calcRow)) - y;
                    
                    double lastY = rowBounds.getItemCoord(Utility.boxRow(getLastDataDisplayRowIncl() - CellPosition.row(1)));
                    
                    // We are pinned when y is less than 0 but less than lastY:
                    if (lastY < 0)
                    {
                        y = lastY;
                        translateFactor.set(0);
                    }
                    else if (y < 0)
                    {
                        y = 0;
                        translateFactor.set(-1);
                    }
                    else
                    {
                        translateFactor.set(0);
                    }
                    
                    return Optional.of(new BoundingBox(
                            x,
                            y,
                            width,
                            height
                    ));
                }

                @Override
                @OnThread(Tag.FXPlatform)
                public Pair<ViewOrder, Node> makeCell()
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
                    
                    BorderPane borderPane = new BorderPane(textField.getNode());
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
                    
                    return new Pair<>(ViewOrder.FLOATING_PINNED, borderPane);
                }

                @Override
                public void adjustForContainerTranslation(Node node, Pair<DoubleExpression, DoubleExpression> translateXY)
                {
                    translateX = translateXY.getFirst().multiply(translateFactor);
                    translateY = translateXY.getSecond().multiply(translateFactor);
                    node.translateXProperty().bind(translateX);
                    node.translateYProperty().bind(translateY);
                }
            };
            // Item for column type:
            FloatingItem columnTypeItem = new FloatingItem()
            {
                @Override
                public Optional<BoundingBox> calculatePosition(VisibleDetails<@AbsRowIndex Integer> rowBounds, VisibleDetails<@AbsColIndex Integer> columnBounds)
                {
                    @AbsColIndex int calcCol = getPosition().columnIndex + CellPosition.col(columnIndex);
                    @AbsRowIndex int calcRow = getPosition().rowIndex + CellPosition.row(2);
                    double x = columnBounds.getItemCoord(Utility.boxCol(calcCol));
                    double y = rowBounds.getItemCoord(Utility.boxRow(calcRow));
                    double width = columnBounds.getItemCoordAfter(Utility.boxCol(calcCol)) - x;
                    double height = rowBounds.getItemCoordAfter(Utility.boxRow(calcRow)) - y;

                    return Optional.of(new BoundingBox(x, y, width, height));
                }

                @Override
                public Pair<ViewOrder, Node> makeCell()
                {
                    Label typeLabel = new TypeLabel(new ReadOnlyObjectWrapper<@Nullable DataType>(column.getColumnType()));
                    typeLabel.getStyleClass().add("table-display-column-title");
                    return new Pair<>(ViewOrder.FLOATING, typeLabel);
                }
            };
            
            columnHeaderItems.add(columnNameItem);
            columnHeaderItems.add(columnTypeItem);
            floatingItems.addItem(columnNameItem);
            floatingItems.addItem(columnTypeItem);
        }
        
        updateParent();
        
    }

    public int getColumnCount(@UnknownInitialization(GridArea.class) DataDisplay this)
    {
        return displayColumns == null ? 0 : displayColumns.size();
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

    // The first data row in absolute position terms (not relative
    // to table), not including any headers
    @OnThread(Tag.FXPlatform)
    public abstract @AbsRowIndex int getFirstDataDisplayRowIncl(@UnknownInitialization(GridArea.class) DataDisplay this);

    // The last data row in absolute position terms (not relative
    // to table), not including any append buttons
    @OnThread(Tag.FXPlatform)
    public abstract @AbsRowIndex int getLastDataDisplayRowIncl(@UnknownInitialization(GridArea.class) DataDisplay this);

    public void cleanupFloatingItems()
    {
        for (FloatingItem columnHeaderItem : columnHeaderItems)
        {
            floatingItems.removeItem(columnHeaderItem);
        }
        floatingItems.removeItem(tableHeaderItem);
    }

    @Override
    public boolean clicked(Point2D screenPosition, CellPosition cellPosition)
    {
        CellPosition pos = getPosition();
        if (!contains(cellPosition))
            return false;
        // In header?
        if (cellPosition.rowIndex == pos.rowIndex)
        {
            withParent(v -> v.select(new EntireTableSelection(this)));
        }
        // In data cells?
        else if (cellPosition.rowIndex >= pos.rowIndex + HEADER_ROWS)
        {
            withParent(v -> v.select(new RectangularTableCellSelection(cellPosition.rowIndex, cellPosition.columnIndex, dataSelectionLimits)));
        }
        // TODO column headers
        
        // If in expand arrows, no selection to be done (although should we trigger here?)
        
        return true;
    }

    private class DestRectangleOverlay extends RectangleOverlayItem
    {
        private final Point2D offsetFromTopLeftOfSource;
        private CellPosition destPosition;
        private Point2D lastMousePosScreen;

        private DestRectangleOverlay(CellPosition curPosition, Bounds originalBoundsOnScreen, double screenX, double screenY)
        {
            this.destPosition = curPosition;
            this.lastMousePosScreen = new Point2D(screenX, screenY);
            this.offsetFromTopLeftOfSource = new Point2D(screenX - originalBoundsOnScreen.getMinX(), screenY - originalBoundsOnScreen.getMinY());
        }

        @Override
        @OnThread(Tag.FXPlatform)
        public Optional<RectangleBounds> calculateBounds(VisibleDetails<@AbsRowIndex Integer> rowBounds, VisibleDetails<@AbsColIndex Integer> columnBounds)
        {
            Optional<@AbsColIndex Integer> columnIndex = columnBounds.getItemIndexForScreenPos(lastMousePosScreen.subtract(offsetFromTopLeftOfSource));
            Optional<@AbsRowIndex Integer> rowIndex = rowBounds.getItemIndexForScreenPos(lastMousePosScreen.subtract(offsetFromTopLeftOfSource));
            if (columnIndex.isPresent() && rowIndex.isPresent())
            {
                destPosition = new CellPosition(rowIndex.get(), columnIndex.get()); 
                return Optional.of(new RectangleBounds(
                    new CellPosition(rowIndex.get(), columnIndex.get()),
                    new CellPosition(rowIndex.get() + CellPosition.row(getCurrentKnownRows() - 1), columnIndex.get() + CellPosition.col(getColumnCount() - 1))
                ));
            }
            return Optional.empty();
        }

        @Override
        protected void style(Rectangle r)
        {
            r.getStyleClass().add("move-table-dest-overlay");
        }

        public void mouseMovedToScreenPos(double screenX, double screenY)
        {
            lastMousePosScreen = new Point2D(screenX, screenY);
        }

        public CellPosition getDestinationPosition()
        {
            return destPosition;
        }
    }
}
