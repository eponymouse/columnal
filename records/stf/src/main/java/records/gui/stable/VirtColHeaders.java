package records.gui.stable;

import com.google.common.collect.ImmutableList;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.gui.stable.VirtScrollStrTextGrid.ScrollLock;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformFunction;
import utility.Pair;
import utility.gui.FXUtility;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * The column headers that sit above a {@link VirtScrollStrTextGrid}.  Support arbitrary content,
 * and resizing.
 */
@OnThread(Tag.FXPlatform)
public class VirtColHeaders implements ScrollBindable
{
    private final Map<Integer, VBox> visibleCells = new HashMap<>();
    private final List<VBox> spareCells = new ArrayList<>();
    private final Region container;
    private final VirtScrollStrTextGrid grid;
    private final FXPlatformFunction<Integer, List<MenuItem>> makeContextMenuItems;
    private final FXPlatformFunction<Integer, ImmutableList<Node>> getContent;
    private final Pane glass;
    private final StackPane stackPane;


    private static final double EDGE_DRAG_TOLERANCE = 8;
    private static final double MIN_COLUMN_WIDTH = 30;

    // Actually dragging to resize?
    private boolean midResizeDrag = false;
    // Could drag-resize if they started at cur pos
    private boolean dragPossible = false;
    // 0 for resizing first header, etc, meaning you are dragging
    // the right edge of that particular header.
    // (Leftmost edge cannot be dragged).  Dragging always
    // resizes header item before it (to left), but not the one after it
    // Only valid if midResizeDrag is true or dragPossible is true
    private int resizingHeader;
    // Horizontal offset from divider being dragged to mouse
    // position while dragging.  Only valid if midResizeDrag is true or dragPossible = true
    private double dragOffset;

    //package-visible
    VirtColHeaders(VirtScrollStrTextGrid grid, FXPlatformFunction<Integer, List<MenuItem>> makeContextMenuItems, FXPlatformFunction<Integer, ImmutableList<Node>> getContent)
    {
        this.grid = grid;
        this.makeContextMenuItems = makeContextMenuItems;
        this.getContent = getContent;
        this.container = new Container();
        // Declaration only so we can suppress warnings:
        @SuppressWarnings("initialization")
        ScrollLock prev = grid.scrollDependents.put(this, ScrollLock.HORIZONTAL);
        container.translateXProperty().bind(grid.container.translateXProperty());
        glass = new Pane();
        glass.setMouseTransparent(true);
        glass.getStyleClass().add("virt-grid-glass");
        stackPane = new StackPane(container, glass);

        // Drag to resize functionality:
        container.setOnMouseMoved(e -> {
            FXUtility.mouse(this).updatePossibleDragPos(e);
            container.setCursor((midResizeDrag || dragPossible) ? Cursor.H_RESIZE : null);
        });
        container.setOnMousePressed(e -> {
            FXUtility.mouse(this).updatePossibleDragPos(e);
            if (dragPossible)
            {
                midResizeDrag = true;
            }
            e.consume();
        });
        container.setOnMouseDragged(e -> {
            // Should be true:
            if (midResizeDrag)
            {
                grid.setColumnWidth(resizingHeader, Math.max(MIN_COLUMN_WIDTH, e.getX() + dragOffset - FXUtility.mouse(this).calculatePositionOfColumnLHS(resizingHeader)));
            }
            e.consume();
        });
        container.setOnMouseReleased(e -> {
            midResizeDrag = false;
            e.consume();
        });
    }

    /**
     * Calculates the X position, in container's local coords, to
     * the left hand side of the given header index.  Note that
     * the position may not actually be visible on-screen,
     * i.e. it may be <= 0 or >= container.getWidth()
     */
    private double calculatePositionOfColumnLHS(int headerIndex)
    {
        // If off to left, count backwards from there:
        if (headerIndex <= grid.getFirstVisibleColIndex())
            return grid.getFirstVisibleColOffset() - grid.sumColumnWidths(headerIndex, grid.getFirstVisibleColIndex());

        return grid.getFirstVisibleColOffset() + grid.sumColumnWidths(grid.getFirstVisibleColIndex(), headerIndex);
    }

    private void updatePossibleDragPos(MouseEvent e)
    {
        int candidateIndex = grid.getFirstVisibleColIndex() - 1;
        double x = grid.getFirstVisibleColOffset();
        while (x < container.getWidth() + EDGE_DRAG_TOLERANCE && candidateIndex < grid.getNumColumns())
        {
            if (candidateIndex >= 0 && Math.abs(x - e.getX()) <= EDGE_DRAG_TOLERANCE)
                break;

            candidateIndex += 1;
            if (candidateIndex < grid.getNumColumns())
                x += grid.getColumnWidth(candidateIndex);
        }

        dragPossible = candidateIndex < grid.getNumColumns();
        if (!midResizeDrag)
        {
            resizingHeader = candidateIndex;
            dragOffset = e.getX() - x;
        }
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public void showAtOffset(@Nullable Pair<Integer, Double> rowAndPixelOffset, @Nullable Pair<Integer, Double> colAndPixelOffset)
    {
        if (colAndPixelOffset != null)
        {
            boolean atLeft = colAndPixelOffset.getFirst() == 0 && colAndPixelOffset.getSecond() >= -5;
            FXUtility.setPseudoclass(glass, "left-shadow", !atLeft);
            container.requestLayout();
        }
    }

    @Override
    public void updateClip()
    {

    }

    @Override
    public void columnWidthChanged(int columnWidth, double newWidth)
    {
        container.requestLayout();
    }

    public Region getNode()
    {
        return stackPane;
    }

    private class Container extends Region
    {
        private Container()
        {
            addEventFilter(ScrollEvent.SCROLL, e -> {
                grid.smoothScroll(e, ScrollLock.BOTH);
                e.consume();
            });
        }

        @Override
        protected double computePrefHeight(double width)
        {
            return 37.0;
        }

        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        protected void layoutChildren()
        {
            int firstDisplayCol = grid.getFirstDisplayCol();
            int lastDisplayColExcl = grid.getLastDisplayColExcl();

            // Remove not-visible cells and put them in spare cells:
            for (Iterator<Entry<Integer, VBox>> iterator = visibleCells.entrySet().iterator(); iterator.hasNext(); )
            {
                Entry<Integer, VBox> vis = iterator.next();
                boolean shouldBeVisible = vis.getKey() >= firstDisplayCol && vis.getKey() < lastDisplayColExcl;
                if (!shouldBeVisible)
                {
                    spareCells.add(vis.getValue());
                    iterator.remove();
                }
            }

            double x = grid.getFirstVisibleColOffset() - grid.sumColumnWidths(firstDisplayCol, grid.getFirstVisibleColIndex());
            for (int colIndex = firstDisplayCol; colIndex < lastDisplayColExcl; colIndex++)
            {
                VBox cell = visibleCells.get(colIndex);
                // If cell isn't present, grab from spareCells:
                if (cell == null)
                {
                    if (!spareCells.isEmpty())
                    {
                        cell = spareCells.remove(spareCells.size() - 1);
                        // Reset state:
                        FXUtility.setPseudoclass(cell, "focused-row", false);
                    }
                    else
                    {
                        cell = new VBox();
                        cell.getStyleClass().add("virt-grid-col-header");
                        VBox newCellFinal = cell;
                        int colIndexFinal = colIndex;
                        cell.setOnContextMenuRequested(e -> {
                            ContextMenu menu = new ContextMenu();
                            menu.getItems().addAll(makeContextMenuItems.apply(colIndexFinal));
                            menu.show(newCellFinal, e.getScreenX(), e.getScreenY());
                        });
                        getChildren().add(cell);
                    }
                    visibleCells.put(colIndex, cell);
                    cell.getChildren().setAll(getContent.apply(colIndex));
                }
                cell.setVisible(true);
                cell.resizeRelocate(x, 0, grid.getColumnWidth(colIndex), getHeight());
                x += grid.getColumnWidth(colIndex);
            }

            // Don't let spare cells be more than two visible rows or columns:
            int maxSpareCells = grid.MAX_EXTRA_ROW_COLS * (lastDisplayColExcl - firstDisplayCol);

            while (spareCells.size() > maxSpareCells)
                getChildren().remove(spareCells.remove(spareCells.size() - 1));

            for (VBox spareCell : spareCells)
            {
                spareCell.relocate(-1000, -1000);
                spareCell.setVisible(false);
            }
        }
    }
}
