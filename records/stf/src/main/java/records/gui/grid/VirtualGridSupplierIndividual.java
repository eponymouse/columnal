package records.gui.grid;

import annotation.units.AbsColIndex;
import annotation.units.AbsRowIndex;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import log.Log;
import org.checkerframework.checker.nullness.qual.KeyFor;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.CellPosition;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformFunction;
import utility.Pair;
import utility.gui.FXUtility;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

/**
 * An implementation of {@link VirtualGridSupplier} that has one node per cell position, and re-uses
 * nodes when scrolling occurs.  Can be used by multiple grid-areas, but assumes that they do not overlap.
 * @param <T> The GUI nodes which will be placed and re-used
 * @param <S> The possible styles for that GUI node (usually best to use an enum, but not required)
 */
@OnThread(Tag.FXPlatform)
public abstract class VirtualGridSupplierIndividual<T extends Node, S> extends VirtualGridSupplier<T>
{
    // Maximum extra rows/cols to keep as spare cells
    private static final int MAX_EXTRA_ROW_COLS = 4;

    // Each grid area that we are handling
    private final Map<GridArea, GridCellInfo<T, S>> gridAreas = new IdentityHashMap<>();

    // All items that are currently in the parent container and laid out properly:
    private final Map<CellPosition, Pair<T, StyleUpdater>> visibleItems = new HashMap<>();
    // All items that are in the parent container, but invisible:
    private final List<T> spareItems = new ArrayList<>();
    private final Collection<S> possibleStyles;
    private final ViewOrder viewOrder;

    protected VirtualGridSupplierIndividual(ViewOrder viewOrder, Collection<S> styleValues)
    {
        this.viewOrder = viewOrder;
        this.possibleStyles = styleValues;
    }
    
    // package-visible
    @Override
    final void layoutItems(ContainerChildren containerChildren, VisibleDetails<@AbsRowIndex Integer> rowBounds, VisibleDetails<@AbsColIndex Integer> columnBounds)
    {
        // Remove not-visible cells and put them in spare cells:
        for (Iterator<Entry<@KeyFor("this.visibleItems") CellPosition, Pair<T, StyleUpdater>>> iterator = visibleItems.entrySet().iterator(); iterator.hasNext(); )
        {
            Entry<@KeyFor("this.visibleItems") CellPosition, Pair<T, StyleUpdater>> vis = iterator.next();
            // Note that due to current checker framework bug, it's important
            // this variable name is not re-used anywhere else in this method:
            CellPosition posToCheck = vis.getKey();

            boolean shouldBeVisible =
                    posToCheck.rowIndex >= rowBounds.firstItemIncl &&
                            posToCheck.rowIndex <= rowBounds.lastItemIncl &&
                            posToCheck.columnIndex >= columnBounds.firstItemIncl &&
                            posToCheck.columnIndex <= columnBounds.lastItemIncl &&
                            gridAreas.values().stream().anyMatch(a -> a.hasCellAt(posToCheck));
            if (!shouldBeVisible)
            {
                spareItems.add(vis.getValue().getFirst());
                vis.getValue().getSecond().stopListening();
                iterator.remove();
            }
        }

        // Layout each row:
        for (@AbsRowIndex int rowIndex = rowBounds.firstItemIncl; rowIndex <= rowBounds.lastItemIncl; rowIndex++)
        {
            final double y = rowBounds.getItemCoord(rowIndex);
            double rowHeight = rowBounds.getItemCoordAfter(rowIndex) - y;
            for (@AbsColIndex int columnIndex = columnBounds.firstItemIncl; columnIndex <= columnBounds.lastItemIncl; columnIndex++)
            {
                final double x = columnBounds.getItemCoord(columnIndex);
                CellPosition cellPosition = new CellPosition(rowIndex, columnIndex);
                Optional<GridCellInfo<T, S>> gridForItem = gridAreas.values().stream().filter(a -> a.hasCellAt(cellPosition)).findFirst();
                if (!gridForItem.isPresent())
                    continue;

                Pair<T, StyleUpdater> cell = visibleItems.get(cellPosition);
                // If cell isn't present, grab from spareCells:
                if (cell == null)
                {
                    if (!spareItems.isEmpty())
                    {
                        cell = withStyle(spareItems.remove(spareItems.size() - 1), gridForItem.get().styleForAllCells());
                        resetForReuse(cell.getFirst());
                    }
                    else
                    {
                        cell = withStyle(makeNewItem(), gridForItem.get().styleForAllCells());
                        containerChildren.add(cell.getFirst(), viewOrder);
                    }

                    visibleItems.put(cellPosition, cell);
                    gridForItem.get().fetchFor(cellPosition, pos -> {
                        Pair<T, StyleUpdater> item = visibleItems.get(pos);
                        return item == null ? null : item.getFirst();
                    });
                }
                else
                {
                    cell.getSecond().listenTo(gridForItem.get().styleForAllCells());
                    if (!gridForItem.get().checkCellUpToDate(cellPosition, cell.getFirst()))
                    {
                        gridForItem.get().fetchFor(cellPosition, pos -> {
                            Pair<T, StyleUpdater> item = visibleItems.get(pos);
                            return item == null ? null : item.getFirst();
                        });
                    }
                }
                cell.getFirst().setVisible(true);
                double nextX = columnBounds.getItemCoordAfter(columnIndex);
                FXUtility.resizeRelocate(cell.getFirst(), x, y, nextX - x, rowHeight);
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

        //Log.debug("Visible item count: " + visibleItems.size() + " spare: " + spareItems.size() + " for " + this);
    }

    private Pair<T, StyleUpdater> withStyle(T t, ObjectExpression<? extends Collection<S>> enumSetObjectExpression)
    {
        return new Pair<>(t, new StyleUpdater(t, enumSetObjectExpression));
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
    public final void addGrid(GridArea gridArea, GridCellInfo<T, S> gridCellInfo)
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
    public static interface GridCellInfo<T, S>
    {
        // Does the GridArea have a cell at the given position?  No assumptions are made
        // about contiguity of grid areas.
        public boolean hasCellAt(CellPosition cellPosition);

        // Takes a position,then sets the content of that cell to match whatever should
        // be shown at that position.  The callback lets you fetch the right cell now or in the
        // future (it may change after this call, if you are thread-hopping you should check again).
        public void fetchFor(CellPosition cellPosition, FXPlatformFunction<CellPosition, @Nullable T> getCell);
        
        // What styles should currently be applied to all cells?  All style items not
        // in this set (but in the range for S) will be removed.
        public ObjectExpression<? extends Collection<S>> styleForAllCells();

        // Check whether a cell which is already in use for that
        // position is up-to-date.  It might not be, for example, if
        // the table has changed bounds or column layout
        public boolean checkCellUpToDate(CellPosition cellPosition, T cellFirst);
    }

    private class StyleUpdater implements ChangeListener<Collection<S>>
    {
        private final ObjectExpression<? extends Collection<S>> styleExpression;
        private final T item;

        public StyleUpdater(T item, ObjectExpression<? extends Collection<S>> styleExpression)
        {
            this.item = item;
            this.styleExpression = styleExpression;
            styleExpression.addListener(FXUtility.mouse(this));
            FXUtility.mouse(this).changed(styleExpression, possibleStyles, styleExpression.getValue());
        }

        @Override
        public void changed(ObservableValue<? extends Collection<S>> observable, Collection<S> oldValue, Collection<S> newValue)
        {
            for (S s : possibleStyles)
            {
                adjustStyle(item, s, newValue.contains(s));
            }
        }

        void stopListening()
        {
            styleExpression.removeListener(this);
        }

        public void listenTo(ObjectExpression<? extends Collection<S>> newStyleExpression)
        {
            if (newStyleExpression != styleExpression)
            {
                stopListening();
                newStyleExpression.addListener(this);
                changed(newStyleExpression, possibleStyles, newStyleExpression.getValue());
            }
        }
    }

    @OnThread(Tag.FX)
    protected abstract void adjustStyle(T item, S style, boolean on);
}
