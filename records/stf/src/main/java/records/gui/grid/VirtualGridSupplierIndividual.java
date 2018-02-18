package records.gui.grid;

import javafx.scene.Node;
import javafx.scene.layout.Pane;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.CellPosition;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformFunction;
import utility.FXPlatformSupplier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

/**
 * An implementation of {@link VirtualGridSupplier} that has one node per cell position, and re-uses
 * nodes when scrolling occurs.  Can be used by multiple grid-areas
 * @param <T>
 */
@OnThread(Tag.FXPlatform)
public abstract class VirtualGridSupplierIndividual<T extends Node> extends VirtualGridSupplier<T>
{
    // Maximum extra rows/cols to keep as spare cells
    private static final int MAX_EXTRA_ROW_COLS = 4;

    // Each grid area that we are handling
    private final Map<GridArea, GridCellInfo<T>> gridAreas = new IdentityHashMap<>();

    // All items that are currently in the parent container and laid out properly:
    private final Map<CellPosition, T> visibleItems = new HashMap<>();
    // All items that are in the parent container, but invisible:
    private final List<T> spareItems = new ArrayList<>();

    // package-visible
    final void layoutItems(List<Node> containerChildren, VisibleDetails rowBounds, VisibleDetails columnBounds)
    {
        // Remove not-visible cells and put them in spare cells:
        for (Iterator<Entry<CellPosition, T>> iterator = visibleItems.entrySet().iterator(); iterator.hasNext(); )
        {
            Entry<CellPosition, T> vis = iterator.next();
            CellPosition pos = vis.getKey();

            boolean shouldBeVisible =
                    pos.rowIndex >= rowBounds.firstItemIncl &&
                            pos.rowIndex <= rowBounds.lastItemIncl &&
                            pos.columnIndex >= columnBounds.firstItemIncl &&
                            pos.columnIndex <= columnBounds.lastItemIncl &&
                            gridAreas.values().stream().anyMatch(a -> a.hasCellAt(pos));
            if (!shouldBeVisible)
            {
                spareItems.add(vis.getValue());
                iterator.remove();
            }
        }

        // Layout each row:
        for (int rowIndex = rowBounds.firstItemIncl; rowIndex <= rowBounds.lastItemIncl; rowIndex++)
        {
            final double y = rowBounds.getItemCoord(rowIndex);
            double rowHeight = rowBounds.getItemCoord(rowIndex + 1) - y;
            for (int columnIndex = columnBounds.firstItemIncl; columnIndex <= columnBounds.lastItemIncl; columnIndex++)
            {
                final double x = columnBounds.getItemCoord(columnIndex);
                CellPosition cellPosition = new CellPosition(rowIndex, columnIndex);
                Optional<GridCellInfo<T>> gridForItem = gridAreas.values().stream().filter(a -> a.hasCellAt(cellPosition)).findFirst();
                if (!gridForItem.isPresent())
                    continue;

                T cell = visibleItems.get(cellPosition);
                // If cell isn't present, grab from spareCells:
                if (cell == null)
                {
                    if (!spareItems.isEmpty())
                    {
                        cell = spareItems.remove(spareItems.size() - 1);
                        resetForReuse(cell);
                    }
                    else
                    {
                        cell = makeNewItem();
                        containerChildren.add(cell);
                    }

                    visibleItems.put(cellPosition, cell);
                    gridForItem.get().fetchFor(cellPosition, pos -> visibleItems.get(pos));
                }
                cell.setVisible(true);
                double nextX = columnBounds.getItemCoord(columnIndex + 1);
                cell.resizeRelocate(x, y, nextX - x, rowHeight);
            }
        }

        // Don't let spare cells be more than N visible rows or columns:
        int maxSpareCells = MAX_EXTRA_ROW_COLS * Math.max(rowBounds.lastItemIncl - rowBounds.firstItemIncl + 1, columnBounds.lastItemIncl - columnBounds.firstItemIncl + 1);

        while (spareItems.size() > maxSpareCells)
            containerChildren.remove(spareItems.remove(spareItems.size() - 1));

        for (T spareCell : spareItems)
        {
            hideItem(spareCell);
        }
    }

    // Make a new cell.
    protected abstract T makeNewItem();

    // Can be over-ridden by subclasses.
    protected void resetForReuse(T cell)
    {
    }

    // Keep item in children, but make it invisible
    private void hideItem(T spareCell)
    {
        spareCell.relocate(-1000, -1000);
        spareCell.setVisible(false);
    }

    /**
     * Adds the grid (and its associated info) to being managed by this supplier.
     */
    public final void addGrid(GridArea gridArea, GridCellInfo<T> gridCellInfo)
    {
        gridAreas.put(gridArea, gridCellInfo);
    }

    /**
     * Removes the grid from being used for this supplier.
     */
    public final void removeGrid(GridArea gridArea)
    {
        gridAreas.remove(gridArea);
    }

    // Used to see if a grid area has a cell *OF OUR TYPE* at the given location
    // (This will vary between different suppliers)
    @OnThread(Tag.FXPlatform)
    public static interface GridCellInfo<T>
    {
        // Does the GridArea have a cell at the given position?  No assumptions are made
        // about contiguity of grid areas.
        public boolean hasCellAt(CellPosition cellPosition);

        // Takes a position,then sets the content of that cell to match whatever should
        // be shown at that position.  The callback lets you fetch the right cell now or in the
        // future (it may change after this call, if you are thread-hopping you should check again).
        public void fetchFor(CellPosition cellPosition, FXPlatformFunction<CellPosition, @Nullable T> getCell);
    }
}
