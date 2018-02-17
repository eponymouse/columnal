package records.gui.grid;

import com.google.common.collect.Sets;
import javafx.geometry.BoundingBox;
import javafx.scene.Node;
import threadchecker.OnThread;
import threadchecker.Tag;

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
 * @param <T>
 */
@OnThread(Tag.FXPlatform)
public abstract class VirtualGridSupplierFloating<T extends Node> extends VirtualGridSupplier<T>
{
    private final Map<FloatingItem<T>, Optional<T>> items = new IdentityHashMap<>();
    private final List<T> toRemove = new ArrayList<>();

    @Override
    void layoutItems(List<Node> containerChildren, VisibleDetails rowBounds, VisibleDetails columnBounds)
    {
        containerChildren.removeAll(toRemove);
        toRemove.clear();
        
        for (Entry<FloatingItem<T>, Optional<T>> item : items.entrySet())
        {
            Optional<BoundingBox> pos = item.getKey().calculatePosition(rowBounds, columnBounds);
            if (pos.isPresent())
            {
                // Should be visible; make sure there is a cell and put in right position:
                if (!item.getValue().isPresent())
                {
                    T newCell = makeCell();
                    containerChildren.add(newCell);
                    item.getKey().useCell(newCell);
                    item.setValue(Optional.of(newCell));
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
    
    protected abstract T makeCell();
    
    public final void addItem(FloatingItem<T> item)
    {
        items.put(item, Optional.empty());
    }
    
    public final void removeItem(FloatingItem<T> item)
    {
        Optional<T> removed = items.remove(item);
        if (removed != null && removed.isPresent())
            toRemove.add(removed.get());
    }

    public static interface FloatingItem<T extends Node>
    {
        // If empty is returned, means not visible.  Otherwise, coords in parent are returned.
        public Optional<BoundingBox> calculatePosition(VisibleDetails rowBounds, VisibleDetails columnBounds);
        
        public void useCell(T item);
    }
}
