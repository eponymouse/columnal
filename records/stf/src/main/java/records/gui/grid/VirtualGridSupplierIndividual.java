package records.gui.grid;

import javafx.scene.Node;
import javafx.scene.layout.Pane;
import records.data.CellPosition;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformSupplier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

@OnThread(Tag.FXPlatform)
public abstract class VirtualGridSupplierIndividual<T extends Node> extends VirtualGridSupplier<T>
{
    private static final int MAX_EXTRA_ROW_COLS = 4;

    private final Map<GridArea, GridCellInfo<T>> gridAreas = new IdentityHashMap<>();

    // All items that are currently in the parent container and laid out properly:
    private final Map<CellPosition, T> visibleItems = new HashMap<>();
    // All items that are in the parent container, but invisible:
    private final List<T> spareItems = new ArrayList<>();

    // package-visible
    void layoutItems(List<Node> containerChildren, VisibleDetails rowBounds, VisibleDetails columnBounds)
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
                    T cellFinal = cell;
                    gridForItem.get().useCellFor(cell, cellPosition, () -> visibleItems.get(cellPosition) == cellFinal);
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

    protected abstract T makeNewItem();

    protected void resetForReuse(T cell)
    {
    }

    private void hideItem(T spareCell)
    {
        spareCell.relocate(-1000, -1000);
        spareCell.setVisible(false);
    }

    public void addGrid(GridArea gridArea, GridCellInfo<T> gridCellInfo)
    {
        gridAreas.put(gridArea, gridCellInfo);
    }

    public void removeGrid(GridArea gridArea)
    {
        gridAreas.remove(gridArea);
    }

    // Used to see if a grid area has a cell *OF OUR TYPE* at the given location
    // (This will vary between different suppliers)
    @OnThread(Tag.FXPlatform)
    public static interface GridCellInfo<T>
    {
        public boolean hasCellAt(CellPosition cellPosition);

        public void useCellFor(T item, CellPosition cellPosition, FXPlatformSupplier<Boolean> samePositionCheck);
    }
}
