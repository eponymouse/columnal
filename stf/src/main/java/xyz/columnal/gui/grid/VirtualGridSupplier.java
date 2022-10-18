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
import javafx.beans.binding.DoubleExpression;
import javafx.geometry.HPos;
import javafx.geometry.Point2D;
import javafx.geometry.VPos;
import javafx.scene.Node;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import xyz.columnal.data.CellPosition;
import xyz.columnal.styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Pair;

import java.util.Optional;
import java.util.OptionalDouble;

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
    protected abstract void layoutItems(ContainerChildren containerChildren, VisibleBounds visibleBounds, VirtualGrid virtualGrid);

    protected void sizesOrPositionsChanged(VisibleBounds visibleBounds)
    {
    }

    /**
     * Gets preferred width for the given column among any nodes that are in
     * that column, where preferred means the width needed to display the full-content.
     * 
     * If no nodes are in that column, returns empty instead.
     */
    public abstract OptionalDouble getPrefColumnWidth(@AbsColIndex int colIndex);

    /**
     * EDITING means it has focus and should receive all mouse events.
     * DIRECTLY_CLICKABLE means if you click it should be activated (e.g. button)
     * NOT_CLICKABLE means there is something there, but clicking does nothing to the cell itself -- although it may trigger a selection of a non-focused cell.
     * (nothing there is indicated by null)
     */
    public static enum ItemState { EDITING, DIRECTLY_CLICKABLE, NOT_CLICKABLE }
    
    /**
     * Is there a cell at the given position and if so what it is its state?
     * If none, return null;
     * The second item in the pair is a tooltip to show on hover
     */
    protected abstract @Nullable Pair<ItemState, @Nullable StyledString> getItemState(CellPosition cellPosition, Point2D screenPosition);

    protected abstract void keyboardActivate(CellPosition cellPosition);
    
    /**
     * Start editing that cell, if possible
     */
    protected void startEditing(@Nullable Point2D screenPosition, CellPosition cellPosition, @Nullable String startType)
    {
    }

    @OnThread(Tag.FXPlatform)
    public static interface ContainerChildren
    {
        // Returns a pair of the translate-X and translate-Y for the container,
        // so you can fight against it if you want to be properly pinned.
        public Pair<DoubleExpression, DoubleExpression> add(Node node, ViewOrder viewOrder);
        
        // Change the view order.  Note you cannot change from/to OVERLAY_ACTIVE
        public void changeViewOrder(Node node, ViewOrder viewOrder);
        
        // Returns the same pair so you can remove listeners if needed
        public Pair<DoubleExpression, DoubleExpression> remove(Node node);
    }
    
    // The order here is critical.  Early in the list appears underneath later in the list.
    // OVERLAY_ACTIVE is special as it appears in its own topmost pane.
    public static enum ViewOrder
    {
        // Grid lines appear underneath everything else:
        GRID_LINES,
        // This includes normal cells, and any header items that behave like normal cells
        // (i.e. no floating position, no overlapping other cells)
        STANDARD_CELLS,
        // A border around a selection of cells, e.g. in the import dialog.
        CELL_SELECTION,
        // This is any overlay which may need to appear in front of a standard cell,
        // such as the column name header (which gets pinned as we scroll down)
        // and the row labels (which can appear in front of other data)
        FLOATING,
        // Table borders are separate items that appear in front as an overlay,
        // because they also cast the drop shadow, and that must appear above
        // the other table items
        TABLE_BORDER,
        // Table hats are in front of most other things but behind popups
        TABLE_HAT,
        // Certain items look like popups, so they must go in front of all else.
        POPUP,
        // Then there's tooltips.
        TOOLTIP,
        // Final, special category: this is the overlays for selected cell, for
        // highlighting a table pick.  This goes on its own pane in front of all
        // else because that way we can blur the rest but keep the pick overlay sharp.
        OVERLAY_ACTIVE
    }

    /**
     * Specifies the visible extents being rendered.
     */
    @OnThread(Tag.FXPlatform)
    public static abstract class VisibleBounds
    {
        // Index of the first column/row visible (inclusive)
        public final @AbsRowIndex int firstRowIncl;
        public final @AbsRowIndex int lastRowIncl;
        public final @AbsColIndex int firstColumnIncl;
        public final @AbsColIndex int lastColumnIncl;

        public VisibleBounds(@AbsRowIndex int firstRowIncl, @AbsRowIndex int lastRowIncl, @AbsColIndex int firstColumnIncl, @AbsColIndex int lastColumnIncl)
        {
            this.firstRowIncl = firstRowIncl;
            this.lastRowIncl = lastRowIncl;
            this.firstColumnIncl = firstColumnIncl;
            this.lastColumnIncl = lastColumnIncl;
        }

        // The X position of the left of the given item index
        @OnThread(Tag.FXPlatform)
        @Pure public abstract double getXCoord(@AbsColIndex int colIndex);

        // The X position of the top of the given item index
        @OnThread(Tag.FXPlatform)
        @Pure public abstract double getYCoord(@AbsRowIndex int rowIndex);
        
        // The X position of the right of the given item index
        @OnThread(Tag.FXPlatform)
        @Pure public final double getXCoordAfter(@AbsColIndex int colIndex)
        {
            return getXCoord(colIndex + CellPosition.col(1));
        }

        // The Y position of the bottom of the given item index
        @OnThread(Tag.FXPlatform)
        @Pure public final double getYCoordAfter(@AbsRowIndex int rowIndex)
        {
            return getYCoord(rowIndex + CellPosition.row(1));
        }

        // The cell position with the top-left that is nearest the given screen X/Y position
        // If biases are non-central, it does not use nearest but instead clamps in that direction.
        public abstract Optional<CellPosition> getNearestTopLeftToScreenPos(Point2D screenPos, HPos horizontalBias, VPos verticalBias);

        public abstract Point2D screenToLayout(Point2D screen);
        
        public abstract Point2D _test_localToScreen(Point2D local);
        
        /**
         * Takes a rectangle, and clamps it so that its extents fall within the portion being rendered.
         * 
         * Returns empty if none of its extents are visible
         */
        public Optional<RectangleBounds> clampVisible(RectangleBounds rectangleBounds)
        {
            return rectangleBounds.intersectWith(new RectangleBounds(new CellPosition(firstRowIncl, firstColumnIncl), new CellPosition(lastRowIncl, lastColumnIncl)));
        }
    }
}
