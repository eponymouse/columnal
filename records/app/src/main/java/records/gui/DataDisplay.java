package records.gui;

import com.google.common.collect.ImmutableList;
import javafx.geometry.BoundingBox;
import javafx.scene.Node;
import javafx.scene.control.Label;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.ColumnId;
import records.data.Table.MessageWhenEmpty;
import records.data.TableOperations;
import records.gui.grid.GridArea;
import records.gui.grid.RectangularTableCellSelection.TableSelectionLimits;
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
import utility.SimulationFunction;

import java.util.ArrayList;
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
    
    private final VirtualGridSupplierFloating columnHeaderSupplier;
    private final List<FloatingItem> columnHeaderItems = new ArrayList<>();
    // Not final because it may changes if user changes the display item or preview options change:
    @OnThread(Tag.FXPlatform)
    protected ImmutableList<ColumnDetails> displayColumns = ImmutableList.of();
    
    public DataDisplay(String initialTableName, MessageWhenEmpty messageWhenEmpty, VirtualGridSupplierFloating columnHeaderSupplier)
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
                  double width = columnBounds.getItemCoord(getPosition().columnIndex + (displayColumns == null ? 0 : displayColumns.size())) - x;
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
                  return new Pair<>(ViewOrder.FLOATING, new Label(initialTableName));
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
                    return new Pair<>(ViewOrder.FLOATING_PINNED, new Label(column.getColumnId().getOutput()));
                }
            };
            columnHeaderItems.add(item);
            columnHeaderSupplier.addItem(item);
        }
        
        updateParent();
        
    }

    public int getColumnCount()
    {
        return displayColumns.size();
    }
}
