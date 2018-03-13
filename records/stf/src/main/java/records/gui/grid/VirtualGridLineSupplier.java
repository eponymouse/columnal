package records.gui.grid;

import annotation.units.AbsColIndex;
import annotation.units.AbsRowIndex;
import com.google.common.collect.Sets;
import javafx.scene.shape.Line;
import org.checkerframework.checker.nullness.qual.KeyFor;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.CellPosition;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;

public class VirtualGridLineSupplier extends VirtualGridSupplier<Line>
{
    private static final String LINE_STYLE_CLASS = "virt-grid-line";
    // Maps from the position (that it is to the left/top of) to the line itself:
    private final HashMap<Integer, Line> xLinesInUse = new HashMap<>();
    private final HashMap<Integer, Line> yLinesInUse = new HashMap<>();
    
    @Override
    void layoutItems(ContainerChildren containerChildren, VisibleBounds visibleBounds)
    {
        double lowestX = visibleBounds.getXCoord(visibleBounds.firstColumnIncl);
        double highestX = visibleBounds.getXCoordAfter(visibleBounds.lastColumnIncl);
        double lowestY = visibleBounds.getYCoord(visibleBounds.firstRowIncl);
        double highestY = visibleBounds.getYCoordAfter(visibleBounds.lastRowIncl);
        
        Set<Line> linesToKeep = Sets.newIdentityHashSet();
        
        // Make sure all the intended lines are there:
        for (@AbsColIndex int i = visibleBounds.firstColumnIncl; i <= visibleBounds.lastColumnIncl; i++)
        {
            Line line = xLinesInUse.get(i);
            double x = visibleBounds.getXCoordAfter(i) - 1.0;
            if (line == null)
            {
                line = new Line();
                line.getStyleClass().add(LINE_STYLE_CLASS);
                containerChildren.add(line, ViewOrder.GRID_LINES);
                xLinesInUse.put(i, line);
            }
            // +0.5 to make line appear in the middle of the pixel:
            line.setStartX(x + 0.5);
            line.setEndX(x + 0.5);
            line.setStartY(lowestY);
            line.setEndY(highestY);
            linesToKeep.add(line);
        }

        for (@AbsRowIndex int i = visibleBounds.firstRowIncl; i <= visibleBounds.lastRowIncl; i++)
        {
            Line line = yLinesInUse.get(i);
            double y = visibleBounds.getYCoordAfter(i) - 1.0;
            if (line == null)
            {
                line = new Line();
                line.getStyleClass().add(LINE_STYLE_CLASS);
                containerChildren.add(line, ViewOrder.GRID_LINES);
                yLinesInUse.put(i, line);
            }
            // +0.5 to make line appear in the middle of the pixel:
            line.setStartY(y + 0.5);
            line.setEndY(y + 0.5);
            line.setStartX(lowestX);
            line.setEndX(highestX);
            linesToKeep.add(line);
        }

        for (Iterator<Entry<@KeyFor("this.xLinesInUse") Integer, Line>> iterator = xLinesInUse.entrySet().iterator(); iterator.hasNext(); )
        {
            Entry<@KeyFor("this.xLinesInUse") Integer, Line> integerLineEntry = iterator.next();
            if (!linesToKeep.contains(integerLineEntry.getValue()))
            {
                containerChildren.remove(integerLineEntry.getValue());
                iterator.remove();
            }
        }
        for (Iterator<Entry<@KeyFor("this.yLinesInUse") Integer, Line>> iterator = yLinesInUse.entrySet().iterator(); iterator.hasNext(); )
        {
            Entry<@KeyFor("this.yLinesInUse") Integer, Line> integerLineEntry = iterator.next();
            if (!linesToKeep.contains(integerLineEntry.getValue()))
            {
                containerChildren.remove(integerLineEntry.getValue());
                iterator.remove();
            }
        }
    }

    @Override
    protected @Nullable ItemState getItemState(CellPosition cellPosition)
    {
        return null;
    }
}
