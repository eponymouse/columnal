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
import annotation.units.AbsRowIndex;
import com.google.common.collect.Sets;
import javafx.geometry.Point2D;
import javafx.scene.shape.Line;
import org.checkerframework.checker.nullness.qual.KeyFor;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.CellPosition;
import xyz.columnal.styled.StyledString;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.Utility;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.OptionalDouble;
import java.util.Set;

public class VirtualGridLineSupplier extends VirtualGridSupplier<Line>
{
    private static final String LINE_STYLE_CLASS = "virt-grid-line";
    // Maps from the position (that it is to the left/top of) to the line itself:
    private final HashMap<Integer, Line> xLinesInUse = new HashMap<>();
    private final HashMap<Integer, Line> yLinesInUse = new HashMap<>();
    
    @Override
    protected void layoutItems(ContainerChildren containerChildren, VisibleBounds visibleBounds, VirtualGrid virtualGrid)
    {
        @AbsColIndex int drawFirstCol = Utility.maxCol(visibleBounds.firstColumnIncl, CellPosition.col(1));
        @AbsRowIndex int drawFirstRow = Utility.maxRow(visibleBounds.firstRowIncl, CellPosition.row(1));
        double lowestX = visibleBounds.getXCoord(drawFirstCol);
        double highestX = visibleBounds.getXCoordAfter(visibleBounds.lastColumnIncl);
        double lowestY = visibleBounds.getYCoord(drawFirstRow);
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
            line.setLayoutX(x + 0.5);
            line.setLayoutY(lowestY);
            line.setEndY(highestY - lowestY);
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
            line.setLayoutY(y + 0.5);
            line.setLayoutX(lowestX);
            line.setEndX(highestX - lowestX);
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
    protected @Nullable Pair<ItemState, @Nullable StyledString> getItemState(CellPosition cellPosition, Point2D screenPos)
    {
        return null;
    }

    @Override
    protected void keyboardActivate(CellPosition cellPosition)
    {
        // Not applicable
    }

    @Override
    public OptionalDouble getPrefColumnWidth(@AbsColIndex int colIndex)
    {
        return OptionalDouble.empty();
    }

    public Collection<Line> _test_getColumnDividers()
    {
        return xLinesInUse.values();
    }

    public Collection<Line> _test_getRowDividers()
    {
        return yLinesInUse.values();
    }
}
