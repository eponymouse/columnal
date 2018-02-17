package records.gui.grid;

import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Line;
import threadchecker.OnThread;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

public class VirtualGridLineSupplier extends VirtualGridSupplier<Line>
{
    private final HashMap<Double, Line> xLinesInUse = new HashMap<>();
    private final HashMap<Double, Line> yLinesInUse = new HashMap<>();
    
    @Override
    void layoutItems(List<Node> containerChildren, VisibleDetails rowBounds, VisibleDetails columnBounds)
    {
        // TODO this whole method is inefficient, it will discard all lines every time we scroll both directions

        double lowestX = columnBounds.getItemCoord(columnBounds.firstItemIncl);
        double highestX = columnBounds.getItemCoord(columnBounds.lastItemIncl + 1);
        double lowestY = rowBounds.getItemCoord(rowBounds.firstItemIncl);
        double highestY = rowBounds.getItemCoord(rowBounds.lastItemIncl + 1);
        
        Map<Line, Boolean> linesToKeep = new IdentityHashMap<>();
        
        // Make sure all the intended lines are there:
        for (int i = columnBounds.firstItemIncl; i <= columnBounds.lastItemIncl; i++)
        {
            double x = columnBounds.getItemCoord(i);
            Line line = xLinesInUse.get(x); 
            if (line == null)
            {
                line = new Line(x, 0, x, 0);
                containerChildren.add(line);
                xLinesInUse.put(x, line);
            }
            line.setStartY(lowestY);
            line.setEndY(highestY);
            linesToKeep.put(line, true);
        }

        for (int i = rowBounds.firstItemIncl; i <= rowBounds.lastItemIncl; i++)
        {
            double y = rowBounds.getItemCoord(i);
            Line line = yLinesInUse.get(y);
            if (line == null)
            {
                line = new Line(0, y, 0, y);
                containerChildren.add(line);
                yLinesInUse.put(y, line);
            }
            line.setStartX(lowestX);
            line.setEndX(highestX);
            linesToKeep.put(line, true);
        }

        for (Iterator<Entry<Double, Line>> iterator = xLinesInUse.entrySet().iterator(); iterator.hasNext(); )
        {
            Entry<Double, Line> integerLineEntry = iterator.next();
            if (!linesToKeep.containsKey(integerLineEntry.getKey()))
                iterator.remove();
        }
        for (Iterator<Entry<Double, Line>> iterator = yLinesInUse.entrySet().iterator(); iterator.hasNext(); )
        {
            Entry<Double, Line> integerLineEntry = iterator.next();
            if (!linesToKeep.containsKey(integerLineEntry.getKey()))
                iterator.remove();
        }
    }
}
