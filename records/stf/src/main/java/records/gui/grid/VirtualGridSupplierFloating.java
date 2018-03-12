package records.gui.grid;

import annotation.units.AbsColIndex;
import annotation.units.AbsRowIndex;
import com.google.common.collect.Sets;
import javafx.beans.binding.DoubleExpression;
import javafx.geometry.BoundingBox;
import javafx.scene.Node;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import records.data.CellPosition;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * An implementation of {@link VirtualGridSupplier} that allows nodes to related to a grid area,
 * but to float depending on that grid's position on screen.  This might be a column header,
 * a message when the table is empty, or so on.
 */
@OnThread(Tag.FXPlatform)
public class VirtualGridSupplierFloating extends VirtualGridSupplier<Node>
{
    private final Set<FloatingItem<?>> items = Sets.newIdentityHashSet();
    private final List<Node> toRemove = new ArrayList<>();

    // Prevent creation from outside the package:
    VirtualGridSupplierFloating()
    {
    }
    
    @Override
    void layoutItems(ContainerChildren containerChildren, VisibleDetails<@AbsRowIndex Integer> rowBounds, VisibleDetails<@AbsColIndex Integer> columnBounds)
    {
        toRemove.forEach(r -> containerChildren.remove(r));
        toRemove.clear();
        
        for (FloatingItem<?> item : items)
        {
            item.updatePosition(containerChildren, rowBounds, columnBounds);
        }
    }

    public final <T extends FloatingItem<?>> T addItem(T item)
    {
        items.add(item);
        return item;
    }
    
    public final void removeItem(FloatingItem item)
    {
        @Nullable Node removed = items.remove(item) ? item.node : null;
        // We don't have access to containerChildren right now to remove, so
        // just queue for removal next time we get laid out:
        if (removed != null)
            toRemove.add(removed);
    }

    @Override
    protected @Nullable ItemState getItemState(CellPosition cellPosition)
    {
        return Utility.filterOutNulls(items.stream().<@Nullable ItemState>map(f -> f.getItemState(cellPosition))).findFirst().orElse(null);
    }

    @OnThread(Tag.FXPlatform)
    public static abstract class FloatingItem<T extends Node>
    {
        private @Nullable T node;
        private final ViewOrder viewOrder;

        protected FloatingItem(ViewOrder viewOrder)
        {
            this.viewOrder = viewOrder;
        }

        // If empty is returned, means not visible (and cell is removed).  Otherwise, coords in parent are returned.
        // BoundingBox not Bounds: should be directly calculated, without passing through a coordinate transformation
        protected abstract Optional<BoundingBox> calculatePosition(VisibleDetails<@AbsRowIndex Integer> rowBounds, VisibleDetails<@AbsColIndex Integer> columnBounds);
        
        // Called when a cell is made.  If calculatePosition always returns Optional.of, then this is only called once:
        protected abstract T makeCell();
        
        // Called once, after makeCell.
        public void adjustForContainerTranslation(T item, Pair<DoubleExpression, DoubleExpression> translateXY)
        {
        }

        /**
         * Is there an item at the given position and if so what it is its state?
         * If none, return null;
         */
        public abstract @Nullable ItemState getItemState(CellPosition cellPosition);
        
        protected final @Pure @Nullable T getNode()
        {
            return node;
        }
        
        // Only called by outer class, so can be private
        private void updatePosition(ContainerChildren containerChildren, VisibleDetails<@AbsRowIndex Integer> rowBounds, VisibleDetails<@AbsColIndex Integer> columnBounds)
        {
            Optional<BoundingBox> pos = calculatePosition(rowBounds, columnBounds);
            if (pos.isPresent())
            {
                // Should be visible; make sure there is a cell and put in right position:
                final @NonNull T nodeFinal;
                if (node == null)
                {
                    nodeFinal = makeCell();
                    Pair<DoubleExpression, DoubleExpression> translateXY = containerChildren.add(nodeFinal, viewOrder);
                    adjustForContainerTranslation(nodeFinal, translateXY);
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
                    containerChildren.remove(node);
                    node = null;
                }
            }
        }
    }
}
