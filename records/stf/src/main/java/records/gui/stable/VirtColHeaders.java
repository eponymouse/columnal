package records.gui.stable;

import com.google.common.collect.ImmutableList;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Region;
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

@OnThread(Tag.FXPlatform)
public class VirtColHeaders implements ScrollBindable
{
    private final Map<Integer, VBox> visibleCells = new HashMap<>();
    private final List<VBox> spareCells = new ArrayList<>();
    private final Region container;
    private final VirtScrollStrTextGrid grid;
    private final FXPlatformFunction<Integer, List<MenuItem>> makeContextMenuItems;
    private final FXPlatformFunction<Integer, ImmutableList<Node>> getContent;
    private int firstVisibleColIndex;
    private double firstVisibleColOffset;


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
        this.firstVisibleColIndex = grid.getFirstVisibleColIndex();
        this.firstVisibleColOffset = grid.getFirstVisibleColOffset();
        container.translateYProperty().bind(grid.container.translateYProperty());
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public void showAtOffset(@Nullable Pair<Integer, Double> rowAndPixelOffset, @Nullable Pair<Integer, Double> colAndPixelOffset)
    {
        if (colAndPixelOffset != null)
        {
            this.firstVisibleColIndex = colAndPixelOffset.getFirst();
            this.firstVisibleColOffset = colAndPixelOffset.getSecond();
            container.requestLayout();
        }
    }

    private double sumColumnWidths(int startColIndexIncl, int endColIndexExcl)
    {
        double total = 0;
        for (int i = startColIndexIncl; i < endColIndexExcl; i++)
        {
            total += grid.getColumnWidth(i);
        }
        return total;
    }

    public Region getNode()
    {
        return container;
    }

    private class Container extends Region
    {
        public Container()
        {
            addEventFilter(ScrollEvent.SCROLL, e -> {
                grid.smoothScroll(e);
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
            // We may not need the +1, but play safe:
            int visibleCols = 0;
            double x = firstVisibleColOffset;
            while (x < getWidth() && firstVisibleColIndex + visibleCols < grid.getNumColumns())
            {
                x += grid.getColumnWidth(firstVisibleColIndex + visibleCols) + grid.GAP;
                visibleCols += 1;
            }
            System.err.println("Visible cols: " + visibleCols);

            // Remove not-visible cells and put them in spare cells:
            for (Iterator<Entry<Integer, VBox>> iterator = visibleCells.entrySet().iterator(); iterator.hasNext(); )
            {
                Entry<Integer, VBox> vis = iterator.next();
                boolean shouldBeVisible =
                        vis.getKey() >= Math.max(0, firstVisibleColIndex - grid.getExtraRows()) &&
                                vis.getKey() < Math.min(grid.getCurrentKnownRows(), firstVisibleColIndex + visibleCols + 2 * grid.getExtraRows());
                if (!shouldBeVisible)
                {
                    spareCells.add(vis.getValue());
                    iterator.remove();
                }
            }

            int firstCol = Math.max(0, firstVisibleColIndex - grid.getExtraCols());
            x = firstVisibleColOffset - sumColumnWidths(firstCol, firstVisibleColIndex);

            for (int colIndex = firstCol; colIndex < Math.min(grid.getNumColumns(), firstVisibleColIndex + visibleCols + 2 * grid.getExtraCols()); colIndex++)
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
                        cell.getStyleClass().add("col-header");
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
                cell.resizeRelocate(x, 0, grid.getColumnWidth(colIndex), getHeight());
                x += grid.getColumnWidth(colIndex) + grid.GAP;
            }

            // Don't let spare cells be more than two visible rows or columns:
            int maxSpareCells = grid.MAX_EXTRA_ROW_COLS * visibleCols;

            while (spareCells.size() > maxSpareCells)
                getChildren().remove(spareCells.remove(spareCells.size() - 1));

            for (VBox spareCell : spareCells)
            {
                spareCell.relocate(10000, 10000);
            }
        }
    }
}
