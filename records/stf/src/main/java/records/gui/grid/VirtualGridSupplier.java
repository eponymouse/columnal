package records.gui.grid;

import annotation.units.AbsColIndex;
import annotation.units.AbsRowIndex;
import javafx.beans.binding.DoubleExpression;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import records.data.CellPosition;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformSupplier;
import utility.Pair;
import utility.gui.FXUtility;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Predicate;

/**
 * A class which manages and supplies nodes of a given type.
 * 
 * When we are displaying a sheet, we don't want to make a GUI node for every cell which would be displayed
 * for, say, a 100,000 long table.  We only actually need GUI nodes for the items currently visible.
 * (We also have a few just off-screen, so that minor scrolling does not cause a delay loading new nodes).
 * 
 * So what we do is have a VirtualGridSupplier for each node type that we may want (one for data cells,
 * but also one for table headers, one for grid lines, and so on).  Each node is responsible for doing
 * the layout of the on-screen nodes, and adding/removing nodes to the GUI pane as we scroll around or as
 * things change (tables get added, resized, etc).  This parent class is very generic: it just has one
 * abstract method for doing the layout.  Most subclasses will want to extend {@link VirtualGridSupplierIndividual},
 * which has extra logic for most common cases.
 */
@OnThread(Tag.FXPlatform)
public abstract class VirtualGridSupplier<T extends Node>
{
    /**
     * Layout the items for the current visible pane.
     * 
     * @param containerChildren The modifiable list of children of the actual GUI pane.  Add/remove nodes to this list. 
     * @param rowBounds The row bounds (vertical) of the current visible items (including any needed for scrolling)
     * @param columnBounds The column bounds (horizontal) of the current visible items (including any needed for scrolling)
     */
    // package-visible
    abstract void layoutItems(ContainerChildren containerChildren, VisibleDetails<@AbsRowIndex Integer> rowBounds, VisibleDetails<@AbsColIndex Integer> columnBounds);
    
    @OnThread(Tag.FXPlatform)
    public static interface ContainerChildren
    {
        // Returns a pair of the translate-X and translate-Y for the container,
        // so you can fight against it if you want to be properly pinned.
        public Pair<DoubleExpression, DoubleExpression> add(Node node, ViewOrder viewOrder);
        
        public void remove(Node node);
    }
    
    public static enum ViewOrder { GRID_LINES, STANDARD, FLOATING, FLOATING_PINNED, OVERLAY_PASSIVE, OVERLAY_ACTIVE }
    
    // Used for both rows and columns, to specify visible extents and divider positions
    // Tag the type T with either @AbsRowIndex or @AbsColIndex
    @OnThread(Tag.FXPlatform)
    public static abstract class VisibleDetails<T extends Integer>
    {
        // Index of the first column/row visible (inclusive)
        public final T firstItemIncl;
        // Index of the last column/row visible (inclusive)
        public final T lastItemIncl;

        public VisibleDetails(T firstItemIncl, T lastItemIncl)
        {
            this.firstItemIncl = firstItemIncl;
            this.lastItemIncl = lastItemIncl;
        }

        // The X/Y position of the left/top of the given item index
        @OnThread(Tag.FXPlatform)
        public abstract double getItemCoord(T itemIndex);

        // The X/Y position of the right/bottom of the given item index
        @SuppressWarnings({"unchecked", "units"})
        @OnThread(Tag.FXPlatform)
        public final double getItemCoordAfter(T itemIndex)
        {
            return getItemCoord((T)(Integer)(itemIndex.intValue() + 1));
        }

        // The item index that contains the given screen X/Y position
        public abstract Optional<T> getItemIndexForScreenPos(Point2D screenPos);
    }
}
