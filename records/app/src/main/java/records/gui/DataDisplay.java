package records.gui;

import com.google.common.collect.ImmutableList;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import log.Log;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import records.data.ColumnId;
import records.data.Table.MessageWhenEmpty;
import records.data.TableId;
import records.data.TableManager;
import records.data.TableOperations;
import records.gui.DataCellSupplier.CellStyle;
import records.gui.grid.GridArea;
import records.gui.grid.VirtualGridSupplier.ViewOrder;
import records.gui.grid.VirtualGridSupplier.VisibleDetails;
import records.gui.grid.VirtualGridSupplierFloating;
import records.gui.grid.VirtualGridSupplierFloating.FloatingItem;
import records.gui.stable.ColumnDetails;
import records.gui.stable.ColumnOperation;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformFunction;
import utility.Pair;
import utility.gui.FXUtility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

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
    
    private final VirtualGridSupplierFloating columnHeaderSupplier;
    private final List<FloatingItem> columnHeaderItems = new ArrayList<>();
    // Not final because it may changes if user changes the display item or preview options change:
    @OnThread(Tag.FXPlatform)
    protected ImmutableList<ColumnDetails> displayColumns = ImmutableList.of();

    protected final SimpleObjectProperty<ImmutableList<CellStyle>> cellStyle = new SimpleObjectProperty<>(ImmutableList.of());
    
    public DataDisplay(@Nullable TableManager tableManager, TableId initialTableName, MessageWhenEmpty messageWhenEmpty, VirtualGridSupplierFloating columnHeaderSupplier)
    {
        super(messageWhenEmpty);
        this.columnHeaderSupplier = columnHeaderSupplier;
        this.columnHeaderSupplier.addItem(new FloatingItem() {
              @Override
              @OnThread(Tag.FXPlatform)
              public Optional<BoundingBox> calculatePosition(VisibleDetails rowBounds, VisibleDetails columnBounds)
              {
                  double x = columnBounds.getItemCoord(getPosition().columnIndex);
                  double y = rowBounds.getItemCoord(getPosition().rowIndex);
                  double width = columnBounds.getItemCoord(getPosition().columnIndex + getColumnCount()) - x;
                  double height = rowBounds.getItemCoord(getPosition().rowIndex + 1) - y;
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
                      overlay[0] = new DestRectangleOverlay(borderPane.localToScreen(borderPane.getBoundsInLocal()), e.getScreenX(), e.getScreenY());
                      Log.debug("Adding rectangle");
                      columnHeaderSupplier.addItem(overlay[0]);
                      ImmutableList.Builder<CellStyle> newStyles = ImmutableList.builder();
                      newStyles.addAll(FXUtility.mouse(DataDisplay.this).cellStyle.get());
                      CellStyle blurStyle = new CellStyle();
                      newStyles.add(blurStyle);
                      FXUtility.mouse(DataDisplay.this).cellStyle.set(newStyles.build());
                      borderPane.setEffect(blurStyle.getEffect());
                      FXUtility.mouse(DataDisplay.this).updateParent();
                  });
                  borderPane.setOnMouseReleased(e -> {
                      if (overlay[0] != null)
                      {
                          Log.debug("Removing rectangle");
                          columnHeaderSupplier.removeItem(overlay[0]);
                          FXUtility.mouse(DataDisplay.this).cellStyle.set(ImmutableList.of());
                          borderPane.setEffect(null);
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
    }

    @OnThread(Tag.FXPlatform)
    public void setColumnsAndRows(ImmutableList<ColumnDetails> columns, @Nullable TableOperations operations, @Nullable FXPlatformFunction<ColumnId, ImmutableList<ColumnOperation>> extraColumnActions)
    {
        // Remove old columns:
        columnHeaderItems.forEach(columnHeaderSupplier::removeItem);
        columnHeaderItems.clear();
        this.displayColumns = columns;
        for (int i = 0; i < columns.size(); i++)
        {
            final int columnIndex = i;
            ColumnDetails column = columns.get(i);
            // Item for column name:
            FloatingItem item = new FloatingItem()
            {
                @Override
                @OnThread(Tag.FXPlatform)
                public Optional<BoundingBox> calculatePosition(VisibleDetails rowBounds, VisibleDetails columnBounds)
                {
                    double x = columnBounds.getItemCoord(getPosition().columnIndex + columnIndex);
                    double y = rowBounds.getItemCoord(getPosition().rowIndex + 1);
                    double width = columnBounds.getItemCoord(getPosition().columnIndex + columnIndex + 1) - x;
                    double height = rowBounds.getItemCoord(getPosition().rowIndex + 2) - y;
                    
                    double lastY = Math.max(y, rowBounds.getItemCoord(getPosition().rowIndex + HEADER_ROWS + getCurrentKnownRows() - 2));
                    return Optional.of(new BoundingBox(
                            x,
                            Math.min(Math.max(0, y), lastY),
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
                    BorderPane borderPane = new BorderPane(textField.getNode());
                    borderPane.getStyleClass().add("table-display-column-title");
                    BorderPane.setAlignment(textField.getNode(), Pos.CENTER_LEFT);
                    BorderPane.setMargin(textField.getNode(), new Insets(0, 0, 0, 2));
                    FXUtility.addChangeListenerPlatformNN(cellStyle, cellStyles -> {
                        borderPane.setEffect(null);
                        for (CellStyle style : cellStyles)
                        {
                            borderPane.setEffect(style.getEffect());
                        }
                    });
                    return new Pair<>(ViewOrder.FLOATING_PINNED, borderPane);
                }
            };
            columnHeaderItems.add(item);
            columnHeaderSupplier.addItem(item);
        }
        
        updateParent();
        
    }

    public int getColumnCount(@UnknownInitialization(GridArea.class) DataDisplay this)
    {
        return displayColumns == null ? 0 : displayColumns.size();
    }

    private class DestRectangleOverlay implements FloatingItem
    {
        private final Point2D offsetFromTopLeftOfSource;
        private Point2D lastMousePosScreen;

        private DestRectangleOverlay(Bounds originalBoundsOnScreen, double screenX, double screenY)
        {
            this.lastMousePosScreen = new Point2D(screenX, screenY);
            this.offsetFromTopLeftOfSource = new Point2D(screenX - originalBoundsOnScreen.getMinX(), screenY - originalBoundsOnScreen.getMinY());
        }

        @Override
        @OnThread(Tag.FXPlatform)
        public Optional<BoundingBox> calculatePosition(VisibleDetails rowBounds, VisibleDetails columnBounds)
        {
            OptionalInt columnIndex = columnBounds.getItemIndexForScreenPos(lastMousePosScreen.subtract(offsetFromTopLeftOfSource));
            OptionalInt rowIndex = rowBounds.getItemIndexForScreenPos(lastMousePosScreen.subtract(offsetFromTopLeftOfSource));
            if (columnIndex.isPresent() && rowIndex.isPresent())
            {
                double x = columnBounds.getItemCoord(columnIndex.getAsInt());
                double y = rowBounds.getItemCoord(rowIndex.getAsInt());
                double width = columnBounds.getItemCoord(columnIndex.getAsInt() + getColumnCount()) - x;
                double height = rowBounds.getItemCoord(rowIndex.getAsInt() + getCurrentKnownRows()) - y;
                return Optional.of(new BoundingBox(x, y, width, height));
            }
            return Optional.empty();
        }

        @Override
        public Pair<ViewOrder, Node> makeCell()
        {
            Rectangle r = new Rectangle() {
                @Override
                public void resize(double width, double height)
                {
                    setWidth(width);
                    setHeight(height);
                }

                @Override
                public boolean isResizable()
                {
                    return true;
                }
            };
            r.setStroke(Color.BLACK);
            r.setFill(Color.TRANSPARENT);
            r.setStrokeWidth(3.0);
            r.setMouseTransparent(true);
            return new Pair<>(ViewOrder.OVERLAY, r);
        }

        public void mouseMovedToScreenPos(double screenX, double screenY)
        {
            lastMousePosScreen = new Point2D(screenX, screenY);
        }
    }
}
