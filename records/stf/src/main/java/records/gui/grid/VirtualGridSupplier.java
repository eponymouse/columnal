package records.gui.grid;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import records.data.CellPosition;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformSupplier;
import utility.gui.FXUtility;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * A class which manages and supplies nodes of a given virtual type
 */
@OnThread(Tag.FXPlatform)
public abstract class VirtualGridSupplier<T extends Node>
{
    abstract void layoutItems(List<Node> containerChildren, VisibleDetails rowBounds, VisibleDetails columnBounds);
    
    // Used for both rows and columns, to specify visible extends and divider positions
    public static abstract class VisibleDetails
    {
        // Index of the first column/row visible (inclusive)
        protected int firstItemIncl;
        // Index of the first column/row visible (inclusive)
        protected int lastItemIncl;

        public VisibleDetails(int firstItemIncl, int lastItemIncl)
        {
            this.firstItemIncl = firstItemIncl;
            this.lastItemIncl = lastItemIncl;
        }

        // The X/Y position of the left/top of the given index position
        @OnThread(Tag.FXPlatform)
        protected abstract double getItemCoord(int itemIndex);
    }
}
