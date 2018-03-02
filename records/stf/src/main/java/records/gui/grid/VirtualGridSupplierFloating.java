package records.gui.grid;

import annotation.units.AbsColIndex;
import annotation.units.AbsRowIndex;
import com.google.common.collect.Sets;
import javafx.beans.binding.DoubleExpression;
import javafx.geometry.BoundingBox;
import javafx.scene.Node;
import org.checkerframework.checker.nullness.qual.KeyFor;
import org.checkerframework.checker.nullness.qual.Nullable;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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
    private final Map<FloatingItem, Optional<Node>> items = new IdentityHashMap<>();
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
        
        for (Entry<@KeyFor("this.items") FloatingItem, Optional<Node>> item : items.entrySet())
        {
            Optional<BoundingBox> pos = item.getKey().calculatePosition(rowBounds, columnBounds);
            if (pos.isPresent())
            {
                // Should be visible; make sure there is a cell and put in right position:
                if (!item.getValue().isPresent())
                {
                    Pair<ViewOrder, Node> itemAndOrder = item.getKey().makeCell();
                    Pair<DoubleExpression, DoubleExpression> translateXY = containerChildren.add(itemAndOrder.getSecond(), itemAndOrder.getFirst());
                    item.getKey().adjustForContainerTranslation(itemAndOrder.getSecond(), translateXY);
                    item.setValue(Optional.of(itemAndOrder.getSecond()));
                }
                // Now that there's a cell there, locate it:
                item.getValue().get().resizeRelocate(pos.get().getMinX(), pos.get().getMinY(), pos.get().getWidth(), pos.get().getHeight());
            }
            else
            {
                // Shouldn't be visible; is it?
                if (item.getValue().isPresent())
                {
                    containerChildren.remove(item.getValue().get());
                    item.setValue(Optional.empty());
                }
            }
        }
    }
    
    public final FloatingItem addItem(FloatingItem item)
    {
        items.put(item, Optional.empty());
        return item;
    }
    
    public final void removeItem(FloatingItem item)
    {
        @Nullable Optional<Node> removed = items.remove(item);
        if (removed != null && removed.isPresent())
            toRemove.add(removed.get());
    }

    @OnThread(Tag.FXPlatform)
    public static interface FloatingItem
    {
        // If empty is returned, means not visible.  Otherwise, coords in parent are returned.
        public Optional<BoundingBox> calculatePosition(VisibleDetails<@AbsRowIndex Integer> rowBounds, VisibleDetails<@AbsColIndex Integer> columnBounds);
        
        public Pair<ViewOrder, Node> makeCell();
        
        public default void adjustForContainerTranslation(Node item, Pair<DoubleExpression, DoubleExpression> translateXY)
        {
        }
    }
}
