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

package xyz.columnal.gui.grid;

import annotation.units.AbsColIndex;
import javafx.beans.binding.DoubleExpression;
import javafx.geometry.BoundingBox;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import xyz.columnal.data.CellPosition;
import xyz.columnal.styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.gui.FXUtility;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.stream.DoubleStream;

/**
 * An implementation of {@link VirtualGridSupplier} that allows nodes to related to a grid area,
 * but to float depending on that grid's position on screen.  This might be a column header,
 * a message when the table is empty, or so on.
 * 
 * There is only a singleton VirtualGridSupplierFloating,
 * which then references many individual FloatingItem.s 
 * 
 * Note: we offer a guarantee that items are iterated through in order that they were added;
 * this is made use of by some table overlays.
 */
@OnThread(Tag.FXPlatform)
public class VirtualGridSupplierFloating extends VirtualGridSupplier<Node>
{
    // We want to maintain order of adding for iteration, and removal is quite rare, so list is best:
    private final List<FloatingItem<?>> items = new ArrayList<>();
    private final List<FloatingItemToRemove<?>> toRemove = new ArrayList<>();

    private static class FloatingItemToRemove<T extends Node>
    {
        private final FloatingItem<T> item;
        private final T node;

        private FloatingItemToRemove(FloatingItem<T> item, T node)
        {
            this.item = item;
            this.node = node;
        }
    }
    
    // Prevent creation from outside the package:
    VirtualGridSupplierFloating()
    {
    }
    
    @Override
    protected void layoutItems(ContainerChildren containerChildren, VisibleBounds visibleBounds, VirtualGrid virtualGrid)
    {
        toRemove.forEach(r -> enactRemove(containerChildren, r));
        toRemove.clear();
        
        for (FloatingItem<?> item : items)
        {
            item.updatePosition(containerChildren, visibleBounds);
        }
    }

    private <T extends Node> void enactRemove(ContainerChildren containerChildren, FloatingItemToRemove<T> r)
    {
        r.item.adjustForContainerTranslation(r.node, containerChildren.remove(r.node), false);
    }

    // Returns true if newly added, false if already present
    public final <T extends FloatingItem<?>> boolean addItem(T item)
    {
        if (!items.contains(item))
        {
            items.add(item);
            return true;
        }
        else
            return false;
    }
    
    // Returns true if removed, false if wasn't present
    public final <T extends Node> boolean removeItem(FloatingItem<T> item)
    {
        @Nullable T removed = items.remove(item) ? item.node : null;
        // We don't have access to containerChildren right now to remove, so
        // just queue for removal next time we get laid out:
        if (removed != null)
            toRemove.add(new FloatingItemToRemove<>(item, removed));
        return removed != null;
    }

    @Override
    protected @Nullable Pair<ItemState, @Nullable StyledString> getItemState(CellPosition cellPosition, Point2D screenPos)
    {
        return Utility.filterOutNulls(items.stream().<@Nullable Pair<ItemState, @Nullable StyledString>>map(f -> f.getItemState(cellPosition, screenPos))).min(Comparator.<Pair<ItemState, @Nullable StyledString>, Integer>comparing(s -> s.getFirst().ordinal())).orElse(null);
    }

    @Override
    protected void sizesOrPositionsChanged(VisibleBounds visibleBounds)
    {
        for (FloatingItem<?> item : items)
        {
            item.sizesOrPositionsChanged(visibleBounds);
        }
    }

    @Override
    protected void keyboardActivate(CellPosition cellPosition)
    {
        // Avoid concurrent modification issues:
        for (FloatingItem<?> item : new ArrayList<@NonNull FloatingItem<?>>(items))
        {
            item.keyboardActivate(cellPosition);
        }
    }

    @Override
    public OptionalDouble getPrefColumnWidth(@AbsColIndex int colIndex)
    {
        return items.stream().flatMapToDouble(n -> {
            OptionalDouble optionalDouble = n.getPrefWidthForColumn(colIndex);
            if (optionalDouble.isPresent())
                return DoubleStream.of(optionalDouble.getAsDouble());
            else
                return DoubleStream.empty();
        }).max();
    }

    /**
     * A wrapper around a GUI item of type T that handles
     * various layout operations.
     * @param <T>
     */
    @OnThread(Tag.FXPlatform)
    public static abstract class FloatingItem<T extends @NonNull Node>
    {
        // null if not created, or has been removed.
        private @Nullable T node;
        private final ViewOrder viewOrder;

        protected FloatingItem(ViewOrder viewOrder)
        {
            this.viewOrder = viewOrder;
        }

        // If empty is returned, means not visible (and cell is removed).  Otherwise, coords in parent are returned.
        // BoundingBox not Bounds: should be directly calculated, without passing through a coordinate transformation
        protected abstract Optional<BoundingBox> calculatePosition(VisibleBounds visibleBounds);
        
        // Called when a cell is made.  If calculatePosition always returns Optional.of, then this is only called once:
        protected abstract T makeCell(VisibleBounds visibleBounds);
        
        // Called once, after makeCell, with adding == true, and once when removed with adding == false
        public void adjustForContainerTranslation(T item, Pair<DoubleExpression, DoubleExpression> translateXY, boolean adding)
        {
        }

        /**
         * Is there an item at the given position and if so what it is its state?
         * If none, return null;
         */
        public abstract @Nullable Pair<ItemState, @Nullable StyledString> getItemState(CellPosition cellPosition, Point2D screenPos);
        
        protected final @Pure @Nullable T getNode(@UnknownInitialization(FloatingItem.class) FloatingItem<T> this)
        {
            return node;
        }

        protected OptionalDouble getPrefWidthForItem(@AbsColIndex int columnIndex, T node)
        {
            return OptionalDouble.empty();
        }
        
        public final OptionalDouble getPrefWidthForColumn(@AbsColIndex int columnIndex)
        {
            return node == null ? OptionalDouble.empty() : getPrefWidthForItem(columnIndex, node);
        }
        
        // Only called by outer class, so can be private
        private void updatePosition(ContainerChildren containerChildren, VisibleBounds visibleBounds)
        {
            Optional<BoundingBox> pos = calculatePosition(visibleBounds);
            if (pos.isPresent())
            {
                // Should be visible; make sure there is a cell and put in right position:
                final @NonNull T nodeFinal;
                if (node == null)
                {
                    nodeFinal = makeCell(visibleBounds);
                    Pair<DoubleExpression, DoubleExpression> translateXY = containerChildren.add(nodeFinal, viewOrder);
                    adjustForContainerTranslation(nodeFinal, translateXY, true);
                    this.node = nodeFinal;
                }
                else
                {
                    nodeFinal = node;
                }
                // Now that there's a cell there, locate it:
                FXUtility.resizeRelocate(nodeFinal, pos.get().getMinX(), pos.get().getMinY(), pos.get().getWidth(), pos.get().getHeight());
            }
            else
            {
                // Shouldn't be visible; is it?
                if (node != null)
                {
                    Pair<DoubleExpression, DoubleExpression> translateXY = containerChildren.remove(node);
                    adjustForContainerTranslation(node, translateXY, false);
                    node = null;
                }
            }
        }

        protected void sizesOrPositionsChanged(VisibleBounds visibleBounds)
        {
        }

        // Activate cell at the given point if something is displayed there
        public abstract void keyboardActivate(CellPosition cellPosition);
    }
}
