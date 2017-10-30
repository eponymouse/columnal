package records.gui.stable;

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

    public Region getNode()
    {
        return stackPane;
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
            return 25.0;
        }

        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        protected void layoutChildren()
        {
            int firstDisplayRow = grid.getFirstDisplayRow();
            int lastDisplayRowExcl = grid.getLastDisplayRowExcl();

            // Remove not-visible cells and put them in spare cells:
            for (Iterator<Entry<Integer, Label>> iterator = visibleCells.entrySet().iterator(); iterator.hasNext(); )
            {
                Entry<Integer, Label> vis = iterator.next();
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
                        cell.getStyleClass().add("virt-grid-row-number");
                        Label newCellFinal = cell;
                        int rowIndexFinal = rowIndex;
                        cell.setOnContextMenuRequested(e -> {
                            ContextMenu menu = new ContextMenu();
                            menu.getStyleClass().add("virt-grid-menu");
                            List<MenuItem> menuItems = makeContextMenuItems.apply(rowIndexFinal);
                            if (!menuItems.isEmpty())
                            {
                                menu.getItems().addAll(menuItems);
                                menu.show(newCellFinal, e.getScreenX(), e.getScreenY());
                            }
                        });
                        getChildren().add(cell);
                    }

                    visibleCells.put(rowIndex, cell);
                    cell.setText("" + rowIndex);
                }
                cell.setVisible(true);
                cell.resizeRelocate(0, y, getWidth(), grid.rowHeight);
                y += grid.rowHeight;
            }

            // Don't let spare cells be more than two visible rows or columns:
            int maxSpareCells = grid.MAX_EXTRA_ROW_COLS * (lastDisplayRowExcl - firstDisplayRow);

            while (spareCells.size() > maxSpareCells)
                getChildren().remove(spareCells.remove(spareCells.size() - 1));

            for (Label spareCell : spareCells)
            {
                spareCell.relocate(-1000, -1000);
                spareCell.setVisible(false);
            }
        }
    }
}
