package records.gui.stable;

import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Region;
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

// For use with VirtScrollStrTextGrid
@OnThread(Tag.FXPlatform)
public class VirtRowLabels implements ScrollBindable
{
    private final Map<Integer, Label> visibleCells = new HashMap<>();
    private final List<Label> spareCells = new ArrayList<>();
    private final Region container;
    private final VirtScrollStrTextGrid grid;
    private final FXPlatformFunction<Integer, List<MenuItem>> makeContextMenuItems;
    private int firstVisibleRowIndex;
    private double firstVisibleRowOffset;

    //package-visible
    VirtRowLabels(VirtScrollStrTextGrid grid, FXPlatformFunction<Integer, List<MenuItem>> makeContextMenuItems)
    {
        this.grid = grid;
        this.makeContextMenuItems = makeContextMenuItems;
        this.container = new Container();
        // Declaration only so we can suppress warnings:
        @SuppressWarnings("initialization")
        ScrollLock prev = grid.scrollDependents.put(this, ScrollLock.VERTICAL);
        this.firstVisibleRowIndex = grid.getFirstVisibleRowIndex();
        this.firstVisibleRowOffset = grid.getFirstVisibleRowOffset();
        container.translateYProperty().bind(grid.container.translateYProperty());
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public void showAtOffset(@Nullable Pair<Integer, Double> rowAndPixelOffset, @Nullable Pair<Integer, Double> colAndPixelOffset)
    {
        if (rowAndPixelOffset != null)
        {
            this.firstVisibleRowIndex = rowAndPixelOffset.getFirst();
            this.firstVisibleRowOffset = rowAndPixelOffset.getSecond();
            container.requestLayout();
        }
    }

    @Override
    public void updateClip()
    {

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
                grid.smoothScroll(e, ScrollLock.BOTH);
                e.consume();
            });
        }

        @Override
        protected double computePrefWidth(double height)
        {
            return 37.0;
        }

        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        protected void layoutChildren()
        {
            // We may not need the +1, but play safe:
            int visibleRows = Math.min(grid.getCurrentKnownRows() - firstVisibleRowIndex, (int)Math.ceil(getHeight() / (grid.rowHeight + grid.GAP)) + 1);

            // Remove not-visible cells and put them in spare cells:
            for (Iterator<Entry<Integer, Label>> iterator = visibleCells.entrySet().iterator(); iterator.hasNext(); )
            {
                Entry<Integer, Label> vis = iterator.next();
                boolean shouldBeVisible =
                    vis.getKey() >= Math.max(0, firstVisibleRowIndex - grid.getExtraRows()) &&
                    vis.getKey() < Math.min(grid.getCurrentKnownRows(), firstVisibleRowIndex + visibleRows + 2 * grid.getExtraRows());
                if (!shouldBeVisible)
                {
                    spareCells.add(vis.getValue());
                    iterator.remove();
                }
            }

            double y = firstVisibleRowOffset - grid.getExtraRows() * (grid.rowHeight + grid.GAP);
            for (int rowIndex = Math.max(0, firstVisibleRowIndex - grid.getExtraRows()); rowIndex < Math.min(grid.getCurrentKnownRows(), firstVisibleRowIndex + visibleRows + 2 * grid.getExtraRows()); rowIndex++)
            {
                Label cell = visibleCells.get(rowIndex);
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
                        cell = new Label();
                        cell.getStyleClass().add("row-number");
                        Label newCellFinal = cell;
                        int rowIndexFinal = rowIndex;
                        cell.setOnContextMenuRequested(e -> {
                            ContextMenu menu = new ContextMenu();
                            menu.getItems().addAll(makeContextMenuItems.apply(rowIndexFinal));
                            menu.show(newCellFinal, e.getScreenX(), e.getScreenY());
                        });
                        getChildren().add(cell);
                    }

                    visibleCells.put(rowIndex, cell);
                    cell.setText("" + rowIndex);
                }
                cell.resizeRelocate(0, y, getWidth(), grid.rowHeight);
                y += grid.rowHeight + grid.GAP;
            }

            // Don't let spare cells be more than two visible rows or columns:
            int maxSpareCells = grid.MAX_EXTRA_ROW_COLS * visibleRows;

            while (spareCells.size() > maxSpareCells)
                getChildren().remove(spareCells.remove(spareCells.size() - 1));

            for (Label spareCell : spareCells)
            {
                spareCell.relocate(10000, 10000);
            }
        }
    }
}
