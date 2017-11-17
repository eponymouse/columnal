package records.gui.stable;

import javafx.geometry.Pos;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.gui.stable.VirtScrollStrTextGrid.ScrollLock;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformFunction;
import utility.Pair;
import utility.gui.FXUtility;
import utility.gui.GUI;

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
    // Each StackPane will have a Label as first child
    private final Map<Integer, StackPane> visibleCells = new HashMap<>();
    private final List<StackPane> spareCells = new ArrayList<>();
    private final Region container;
    private final VirtScrollStrTextGrid grid;
    private final FXPlatformFunction<Integer, List<MenuItem>> makeContextMenuItems;
    private final Pane glass;
    private final StackPane stackPane;

    //package-visible
    VirtRowLabels(VirtScrollStrTextGrid grid, FXPlatformFunction<Integer, List<MenuItem>> makeContextMenuItems)
    {
        this.grid = grid;
        this.makeContextMenuItems = makeContextMenuItems;
        this.container = new Container();
        // Declaration only so we can suppress warnings:
        @SuppressWarnings("initialization")
        ScrollLock prev = grid.scrollDependents.put(this, ScrollLock.VERTICAL);
        FXUtility.addChangeListenerPlatformNN(grid.currentKnownRows(), r -> container.requestLayout());
        FXUtility.addChangeListenerPlatformNN(grid.heightProperty(), r -> container.requestLayout());
        container.translateYProperty().bind(grid.container.translateYProperty());
        glass = new Pane();
        glass.setMouseTransparent(true);
        glass.getStyleClass().add("virt-grid-glass");
        stackPane = new StackPane(container, glass);
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public void showAtOffset(@Nullable Pair<Integer, Double> rowAndPixelOffset, @Nullable Pair<Integer, Double> colAndPixelOffset)
    {
        if (rowAndPixelOffset != null)
        {
            boolean atTop = rowAndPixelOffset.getFirst() == 0 && rowAndPixelOffset.getSecond() >= -5;
            FXUtility.setPseudoclass(glass, "top-shadow", !atTop);
            container.requestLayout();
        }
    }

    @Override
    public void updateClip()
    {

    }

    @Override
    public void columnWidthChanged(int columnIndex, double newWidth)
    {
        // Doesn't affect us
    }

    @Override
    public @OnThread(Tag.FXPlatform) void columnsChanged()
    {
        // Doesn't affect us
    }

    public Region getNode()
    {
        return stackPane;
    }

    private class Container extends Region
    {
        public Container()
        {
            getStyleClass().add("virt-grid-row-container");

            addEventFilter(ScrollEvent.SCROLL, e -> {
                grid.smoothScroll(e, ScrollLock.BOTH);
                e.consume();
            });
        }

        @Override
        protected double computePrefWidth(double height)
        {
            return 25.0;
        }

        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        protected void layoutChildren()
        {
            int firstDisplayRow = grid.getFirstDisplayRow();
            int lastDisplayRowExcl = grid.getLastDisplayRowExcl();

            // Remove not-visible cells and put them in spare cells:
            for (Iterator<Entry<Integer, StackPane>> iterator = visibleCells.entrySet().iterator(); iterator.hasNext(); )
            {
                Entry<Integer, StackPane> vis = iterator.next();
                boolean shouldBeVisible =
                    vis.getKey() >= firstDisplayRow &&
                    vis.getKey() < lastDisplayRowExcl;
                if (!shouldBeVisible)
                {
                    spareCells.add(vis.getValue());
                    iterator.remove();
                }
            }

            double y = grid.getFirstVisibleRowOffset() - (grid.getFirstVisibleRowIndex() - firstDisplayRow) * grid.rowHeight;
            for (int rowIndex = firstDisplayRow; rowIndex < lastDisplayRowExcl; rowIndex++)
            {
                StackPane cell = visibleCells.get(rowIndex);
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
                        cell = GUI.withRightClickHint(new Label(), Pos.TOP_LEFT);
                        cell.getStyleClass().add("virt-grid-row-number");
                        getChildren().add(cell);
                    }

                    int rowIndexFinal = rowIndex;
                    StackPane cellFinal = cell;
                    cell.setOnContextMenuRequested(e -> {
                        ContextMenu menu = new ContextMenu();
                        menu.getStyleClass().add("virt-grid-menu");
                        List<MenuItem> menuItems = makeContextMenuItems.apply(rowIndexFinal);
                        if (!menuItems.isEmpty())
                        {
                            menu.getItems().addAll(menuItems);
                            menu.show(cellFinal, e.getScreenX(), e.getScreenY());
                        }
                    });

                    visibleCells.put(rowIndex, cell);
                    ((Label)cell.getChildren().get(0)).setText("" + rowIndex);
                }
                cell.setVisible(true);
                cell.resizeRelocate(0, y, getWidth(), grid.rowHeight);
                y += grid.rowHeight;
            }

            // Don't let spare cells be more than two visible rows or columns:
            int maxSpareCells = grid.MAX_EXTRA_ROW_COLS * (lastDisplayRowExcl - firstDisplayRow);

            while (spareCells.size() > maxSpareCells)
                getChildren().remove(spareCells.remove(spareCells.size() - 1));

            for (StackPane spareCell : spareCells)
            {
                spareCell.relocate(-1000, -1000);
                spareCell.setVisible(false);
            }
        }
    }
}
