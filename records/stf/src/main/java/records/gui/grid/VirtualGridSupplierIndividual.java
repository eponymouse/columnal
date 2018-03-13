package records.gui.grid;

import annotation.units.AbsColIndex;
import annotation.units.AbsRowIndex;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import org.checkerframework.checker.nullness.qual.KeyFor;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.CellPosition;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformFunction;
import utility.Pair;
import utility.Utility;
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
import java.util.stream.Stream;

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
    private final Map<CellPosition, ItemDetails<T>> visibleItems = new HashMap<>();
    // All items that are in the parent container, but invisible:
    private final List<T> spareItems = new ArrayList<>();
    private final Collection<S> possibleStyles;
    private final ViewOrder viewOrder;
    
    private class ItemDetails<T>
    {
        private final T node;
        private final StyleUpdater styleUpdater;
        private final GridAreaCellPosition gridAreaCellPosition;
        private final GridCellInfo<T, S> originator;

        private ItemDetails(T node, StyleUpdater styleUpdater, GridCellInfo<T, S> originator, GridAreaCellPosition gridAreaCellPosition)
        {
            this.node = node;
            this.styleUpdater = styleUpdater;
            this.gridAreaCellPosition = gridAreaCellPosition;
            this.originator = originator;
        }
    }

    protected VirtualGridSupplierIndividual(ViewOrder viewOrder, Collection<S> styleValues)
    {
        this.viewOrder = viewOrder;
        this.possibleStyles = styleValues;
    }
    
    // package-visible
    @Override
    final void layoutItems(ContainerChildren containerChildren, VisibleBounds visibleBounds)
    {
        // Remove not-visible cells and put them in spare cells:
        for (Iterator<Entry<@KeyFor("this.visibleItems") CellPosition, ItemDetails<T>>> iterator = visibleItems.entrySet().iterator(); iterator.hasNext(); )
        {
            Entry<@KeyFor("this.visibleItems") CellPosition, ItemDetails<T>> vis = iterator.next();
            // Note that due to current checker framework bug, it's important
            // this variable name is not re-used anywhere else in this method:
            CellPosition posToCheck = vis.getKey();

            boolean shouldBeVisible =
                    posToCheck.rowIndex >= visibleBounds.firstRowIncl &&
                            posToCheck.rowIndex <= visibleBounds.lastRowIncl &&
                            posToCheck.columnIndex >= visibleBounds.firstColumnIncl &&
                            posToCheck.columnIndex <= visibleBounds.lastColumnIncl &&
                            vis.getValue().originator.cellAt(posToCheck) != null;
            if (!shouldBeVisible)
            {
                spareItems.add(vis.getValue().node);
                vis.getValue().styleUpdater.stopListening();
                iterator.remove();
            }
        }

        // Layout each row:
        for (@AbsRowIndex int rowIndex = visibleBounds.firstRowIncl; rowIndex <= visibleBounds.lastRowIncl; rowIndex++)
        {
            final double y = visibleBounds.getYCoord(rowIndex);
            double rowHeight = visibleBounds.getYCoordAfter(rowIndex) - y;
            for (@AbsColIndex int columnIndex = visibleBounds.firstColumnIncl; columnIndex <= visibleBounds.lastColumnIncl; columnIndex++)
            {
                final double x = visibleBounds.getXCoord(columnIndex);
                CellPosition cellPosition = new CellPosition(rowIndex, columnIndex);
                Optional<Pair<GridCellInfo<T, S>, GridAreaCellPosition>> gridForItemResult = gridAreas.values().stream().flatMap(a -> Utility.streamNullable(a.cellAt(cellPosition)).map(c -> new Pair<>(a, c))).findFirst();
                if (!gridForItemResult.isPresent())
                    continue;

                GridCellInfo<T, S> gridForItem = gridForItemResult.get().getFirst();

                ItemDetails<T> cell = visibleItems.get(cellPosition);
                // If cell isn't present, grab from spareCells:
                if (cell == null)
                {
                    Pair<T, StyleUpdater> newCell;
                    if (!spareItems.isEmpty())
                    {
                        newCell = withStyle(spareItems.remove(spareItems.size() - 1), gridForItem.styleForAllCells());
                        resetForReuse(newCell.getFirst());
                    }
                    else
                    {
                        newCell = withStyle(makeNewItem(), gridForItem.styleForAllCells());
                        containerChildren.add(newCell.getFirst(), viewOrder);
                    }
                    cell = new ItemDetails<>(newCell.getFirst(), newCell.getSecond(), gridForItem, gridForItemResult.get().getSecond());

                    visibleItems.put(cellPosition, cell);
                    gridForItem.fetchFor(cell.gridAreaCellPosition, pos -> {
                        ItemDetails<T> item = visibleItems.get(pos);
                        return item == null ? null : item.node;
                    });
                }
                else
                {
                    cell.styleUpdater.listenTo(gridForItem.styleForAllCells());
                    if (cell.originator != gridForItem 
                        || !cell.gridAreaCellPosition.equals(gridForItemResult.get().getSecond())
                        || !gridForItem.checkCellUpToDate(cell.gridAreaCellPosition, cell.node))
                    {
                        gridForItem.fetchFor(cell.gridAreaCellPosition, pos -> {
                            ItemDetails<T> item = visibleItems.get(pos);
                            return item == null ? null : item.node;
                        });
                    }
                }
                cell.node.setVisible(true);
                double nextX = visibleBounds.getXCoordAfter(columnIndex);
                FXUtility.resizeRelocate(cell.node, x, y, nextX - x, rowHeight);
            }
        }

        // Don't let spare cells be more than N visible rows or columns:
        int maxSpareCells = MAX_EXTRA_ROW_COLS * Math.max(visibleBounds.lastRowIncl - visibleBounds.firstRowIncl + 1, visibleBounds.lastColumnIncl - visibleBounds.firstRowIncl + 1);

        while (spareItems.size() > maxSpareCells)
            containerChildren.remove(spareItems.remove(spareItems.size() - 1));

        for (T spareCell : spareItems)
        {
            hideItem(spareCell);
        }
        
        styleTogether(visibleItems.values().stream().collect(ImmutableListMultimap.<ItemDetails<T>, GridCellInfo<T, S>, T>flatteningToImmutableListMultimap(d -> d.originator, d -> Stream.of(d.node))).asMap());
        
        //Log.debug("Visible item count: " + visibleItems.size() + " spare: " + spareItems.size() + " for " + this);
    }

    protected void styleTogether(ImmutableMap<GridCellInfo<T, S>, Collection<T>> visibleNodes)
    {
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
    
    protected final boolean hasGrid(GridArea gridArea)
    {
        return gridAreas.containsKey(gridArea);
    }

    /**
     * Used to see if a grid area has a cell *OF OUR TYPE* at the given location
     * T and S are same as for {@link VirtualGridSupplierIndividual}
     */
    @OnThread(Tag.FXPlatform)
    public static interface GridCellInfo<T, S>
    {
        // Does the GridArea have a cell of this type at the given position?  No assumptions are made
        // about contiguity of grid areas, we ask all grid cell infos for all positions.
        // If there is a cell, return its position, else return null
        public @Nullable GridAreaCellPosition cellAt(CellPosition cellPosition);

        // Takes a position,then sets the content of that cell to match whatever should
        // be shown at that position.  The callback lets you fetch the right cell now or in the
        // future (it may change after this call, if you are thread-hopping you should check again).
        public void fetchFor(GridAreaCellPosition cellPosition, FXPlatformFunction<CellPosition, @Nullable T> getCell);
        
        // What styles should currently be applied to all cells?  All style items not
        // in this set (but in the range for S) will be removed.
        public ObjectExpression<? extends Collection<S>> styleForAllCells();

        // Check whether a cell which is already in use for that
        // position is up-to-date.  It might not be, for example, if
        // the table has changed bounds or column layout
        public boolean checkCellUpToDate(GridAreaCellPosition cellPosition, T cellFirst);
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
    
    protected final @Nullable T getItemAt(CellPosition cellPosition)
    {
        return Utility.getIfPresent(visibleItems, cellPosition).map(p -> p.node).orElse(null);
    }

    @Override
    protected final @Nullable ItemState getItemState(CellPosition cellPosition)
    {
        @Nullable T item = getItemAt(cellPosition);
        return item == null ? null : getItemState(item);
    }

    protected abstract ItemState getItemState(T item);
}
