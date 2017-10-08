package records.gui.stable;

import javafx.animation.AnimationTimer;
import javafx.animation.Interpolator;
import javafx.application.Platform;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Region;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.error.InternalException;
import records.error.UserException;
import records.gui.stf.EditorKitSimpleLabel;
import records.gui.stf.StructuredTextField;
import records.gui.stf.StructuredTextField.EditorKit;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.SimulationFunction;
import utility.Utility;
import utility.Workers;
import utility.Workers.Priority;
import utility.gui.FXUtility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

/**
 * This is a lot like a VirtualizedScrollPane of HBox of
 * StructuredTextField, which was the original model for displaying
 * data tables.  But it turned out to be too slow, so it's rewritten
 * here to take advantage of several aspects:
 *  - We can virtualize horizontally and vertically
 *  - We can re-use cells by replacing their content components
 *    (i.e. all cells have the same GUI element type)
 *  - Columns have the same width all the way down, and rows
 *    have same height throughout the grid.
 */
@OnThread(Tag.FXPlatform)
public class VirtScrollStrTextGrid implements EditorKitCallback
{
    private static final double GAP = 1;

    // Cells which are visible, organised as a 2D array
    // (inner is a row, outer is list of rows)
    private final Map<CellPosition, StructuredTextField> visibleCells;
    private int firstVisibleColumnIndex;
    private int firstVisibleRowIndex;
    // Offset of top visible cell.  Always -rowHeight <= y <= 0
    private double firstVisibleColumnOffset;
    private double firstVisibleRowOffset;
    private int visibleRowCount;
    private int visibleColumnCount;

    // TODO: could try to use translate to make scrolling faster?

    // TODO add another sidestrip class for virtualizing row/column headers (line numbers and column names), then add listener to do the updating.

    // Cells which are spare: they are still members of the
    // parent pane to avoid costs of removing and re-adding,
    // but they are held at an off-screen position.
    private final ArrayList<StructuredTextField> spareCells;

    private double rowHeight;
    // This is a minimum number of rows known to be in the table:
    @OnThread(Tag.FXPlatform)
    private int currentKnownRows = 0;
    // A function to ask if the row index is valid.  If isRowValid(n)
    // returns false, it's guaranteed that isRowValid(m) for m>=n is also false.
    @OnThread(Tag.FXPlatform)
    private @Nullable SimulationFunction<Integer, Boolean> isRowValid;
    private double[] columnWidths;

    private final ObjectProperty<@Nullable CellPosition> focusedCell = new SimpleObjectProperty<>(null);

    private final ValueLoadSave loadSave;

    private final Container container;
    private final Map<VirtScrollStrTextGrid, ScrollLock> scrollDependents = new IdentityHashMap<>();
    // How many extra rows to show off-screen each side, to account for scrolling (when actual display can lag logical display):
    private int extraRows = 0;

    public VirtScrollStrTextGrid(ValueLoadSave loadSave)
    {
        visibleCells = new HashMap<>();
        firstVisibleColumnIndex = 0;
        firstVisibleRowIndex = 0;
        firstVisibleColumnOffset = 0;
        firstVisibleRowOffset = 0;
        spareCells = new ArrayList<>();
        rowHeight = 24;
        columnWidths = new double[0];
        this.loadSave = loadSave;

        container = new Container();
    }

    public ObjectExpression<@Nullable CellPosition> focusedCellProperty()
    {
        return focusedCell;
    }

    public void scrollYToPixel(double y)
    {
        // Can't scroll to negative:
        if (y < 0)
            y = 0;

        // So, if y == 0 then we make first cell the top and offset == 0
        // If y < 1*rowHeight, still first cell, and offset == -y
        // If y < 2*rowHeight, second cell, and offset == rowHeight - y
        // General pattern: divide by rowHeight and round down to get topCell
        // Then offset is topCell*rowHeight - y
        int topCell = (int)Math.floor(y / (rowHeight + GAP));
        showAtOffset(topCell, (topCell * (rowHeight + GAP)) - y, firstVisibleColumnIndex, firstVisibleColumnOffset);
    }

    private void updateKnownRows()
    {
        final int prevKnownRows = currentKnownRows;
        if (prevKnownRows > firstVisibleRowIndex + visibleRowCount + 200)
        {
            // If there's more than 200 unshown, that will do as an estimate:
            return;
        }

        int searchMax = firstVisibleRowIndex + visibleRowCount + 250;
        @Nullable SimulationFunction<Integer, Boolean> isRowValidFinal = this.isRowValid;
        Workers.onWorkerThread("Calculating number of rows", Priority.FETCH, () -> {
            int knownRows;
            for (knownRows = prevKnownRows + 1; knownRows < searchMax; knownRows++)
            {
                try
                {
                    if (isRowValidFinal == null || !isRowValidFinal.apply(knownRows))
                        break;
                }
                catch (InternalException | UserException e)
                {
                    Utility.log(e);
                    break;
                }
            }
            int knownRowsFinal = knownRows - 1;
            Platform.runLater(() -> {
                currentKnownRows = knownRowsFinal;
                container.requestLayout();
            });
        });
    }

    public void scrollXToPixel(double targetX)
    {
        double x = Math.max(targetX, 0.0);
        for (int col = 0; col < columnWidths.length; col++)
        {
            if (x < columnWidths[col])
            {
                // Stop here:
                showAtOffset(firstVisibleRowIndex, firstVisibleRowOffset, col, -x);
                break;
            }
            x -= columnWidths[col] + GAP;
        }
    }

    // Focuses cell so that you can navigate around with keyboard
    public void focusCell(CellPosition cellPosition)
    {
        visibleCells.forEach((visPos, visCell) -> {
            FXUtility.setPseudoclass(visCell, "focused-cell", visPos.equals(cellPosition));

        });
        focusedCell.set(cellPosition);
        container.requestFocus();
    }

    // Edits cell so that you can start typing content
    public void editCell(int rowIndex, int columnIndex)
    {
        // TODO
    }

    public boolean isEditingCell(int rowIndex, int columnIndex)
    {
        return false; // TODO
    }

    public void scrollXBy(double x)
    {
        double currentX = 0;
        for (int col = 0; col < firstVisibleColumnIndex; col++)
            currentX += columnWidths[col] + GAP;
        currentX -= firstVisibleColumnOffset;
        scrollXToPixel(currentX + x);
    }

    public void scrollYBy(double y)
    {
        scrollYToPixel(firstVisibleRowIndex * (rowHeight + GAP) - firstVisibleRowOffset + y);
    }

    // This is the canonical scroll method which all scroll
    // attempts should pass through, to avoid duplicating the
    // update code
    public void showAtOffset(int row, double rowPixelOffset, int col, double colPixelOffset)
    {
        // Shouldn't happen, but clamp row and col:
        if (row < 0)
            row = 0;
        if (col < 0)
            col = 0;
        this.firstVisibleRowOffset = rowPixelOffset;
        this.firstVisibleRowIndex = row;
        this.firstVisibleColumnOffset = colPixelOffset;
        this.firstVisibleColumnIndex = col;
        scrollDependents.forEach((grid, lock) -> {
            int targetRow = grid.firstVisibleRowIndex;
            int targetCol = grid.firstVisibleColumnIndex;
            double targetRowOffset = grid.firstVisibleRowOffset;
            double targetColOffset = grid.firstVisibleColumnOffset;
            if (lock.includesVertical())
            {
                targetRow = firstVisibleRowIndex;
                targetRowOffset = firstVisibleRowOffset;
            }
            
            if (lock.includesHorizontal())
            {
                targetCol = firstVisibleColumnIndex;
                targetColOffset = firstVisibleColumnOffset;
            }
            grid.showAtOffset(targetRow, targetRowOffset, targetCol, targetColOffset);
        });
        updateKnownRows();
        container.requestLayout();
    }

    public void bindScroll(VirtScrollStrTextGrid src, ScrollLock lock)
    {
        src.scrollDependents.put(this, lock);
        if (lock.includesVertical())
            scrollYToPixel(src.firstVisibleRowIndex * src.rowHeight + src.firstVisibleColumnOffset);
        // TODO support horizontal too
    }

    public static enum ScrollLock
    {
        HORIZONTAL, VERTICAL, BOTH;

        public boolean includesVertical()
        {
            return this == VERTICAL || this == BOTH;
        }

        public boolean includesHorizontal()
        {
            return this == HORIZONTAL || this == BOTH;
        }
    }

    public static interface ValueLoadSave
    {
        @OnThread(Tag.FXPlatform)
        void fetchEditorKit(int rowIndex, int colIndex, EditorKitCallback setEditorKit);
    }

    public void setData(SimulationFunction<Integer, Boolean> isRowValid, double[] columnWidths)
    {
        // Snap to top:
        firstVisibleRowIndex = 0;
        firstVisibleColumnIndex = 0;
        firstVisibleColumnOffset = 0;
        firstVisibleRowOffset = 0;

        // Empty previous:
        spareCells.addAll(visibleCells.values());
        visibleCells.clear();

        // These variables resize the number of elements,
        // and then layout actually rejigs the display:
        this.isRowValid = isRowValid;
        this.currentKnownRows = 0;
        updateKnownRows();
        this.columnWidths = Arrays.copyOf(columnWidths, columnWidths.length);

        container.requestLayout();
    }

    @Override
    public @OnThread(Tag.FXPlatform) void loadedValue(int rowIndex, int colIndex, EditorKit<?> editorKit)
    {
        CellPosition cellPosition = new CellPosition(rowIndex, colIndex);
        // Check cell hasn't been re-used since:
        StructuredTextField cell = visibleCells.get(cellPosition);
        if (cell != null)
            cell.resetContent(editorKit);
    }

    private @Nullable CellPosition getCellPositionAt(double x, double y)
    {
        int rowIndex;
        int colIndex;
        x -= firstVisibleColumnOffset;
        for (colIndex = firstVisibleColumnIndex; colIndex < columnWidths.length; colIndex++)
        {
            x -= columnWidths[colIndex];
            if (x < 0.0)
            {
                break;
            }
        }
        if (x > 0.0)
            return null;
        y -= firstVisibleRowOffset;
        rowIndex = (int)Math.floor(y / (rowHeight + GAP)) + firstVisibleRowIndex;
        if (rowIndex >= currentKnownRows)
            return null;
        return new CellPosition(rowIndex, colIndex);
    }

    public Region getNode()
    {
        return container;
    }

    @OnThread(Tag.FXPlatform)
    private class Container extends Region
    {
        // AnimationTimer is run every frame, and so lets us do smooth scrolling:
        private final AnimationTimer scroller;
        // Start time of current animation (scrolling again resets this) and target end time:
        private long scrollStartNanos;
        private long scrollEndNanos;
        // Always heading towards zero:
        private double scrollOffset;
        // Scroll offset at scrollStartNanos
        private double scrollStartOffset;
        private final long SCROLL_TIME_NANOS = 300_000_000;

        public Container()
        {
            getStyleClass().add("virt-grid");

            addEventFilter(MouseEvent.MOUSE_CLICKED, clickEvent -> {
                if (clickEvent.getClickCount() == 1)
                {
                    @Nullable CellPosition cellPosition = getCellPositionAt(clickEvent.getX(), clickEvent.getY());
                    if (cellPosition != null)
                        focusCell(cellPosition);
                }
                clickEvent.consume();
            });

            scroller = new AnimationTimer()
            {
                @Override
                public void handle(long now)
                {
                    // If scroll end time in future, and our target scroll is more than 1/8th pixel away:
                    if (scrollEndNanos > now && Math.abs(scrollOffset) > 0.125)
                    {
                        scrollOffset = Interpolator.EASE_BOTH.interpolate(scrollStartOffset, 0, (double)(now - scrollStartNanos) / (scrollEndNanos - scrollStartNanos));
                        setTranslateY(scrollOffset);
                    }
                    else
                    {
                        setTranslateY(0.0);
                        scrollOffset = 0.0;
                        extraRows = 0;
                        stop();
                    }
                }
            };

            // Filter because we want to steal it from the cells themselves:
            addEventFilter(ScrollEvent.ANY, scrollEvent -> {
                // Reset start and end time:
                scrollStartNanos = System.nanoTime();
                scrollEndNanos = scrollStartNanos + SCROLL_TIME_NANOS;

                scrollXBy(-scrollEvent.getDeltaX());

                // We subtract from current offset, because we may already be mid-scroll in which
                // case we don't want to jump, just want to add on (we will go faster to cover this
                // because scroll will be same duration but longer):
                scrollOffset -= scrollEvent.getDeltaY();
                scrollStartOffset = scrollOffset;
                extraRows = (int)Math.ceil(Math.abs(scrollOffset) / (rowHeight + GAP));
                scrollYBy(-scrollEvent.getDeltaY());
                setTranslateY(scrollOffset);

                // Start the smooth scrolling animation:
                scroller.start();

                scrollEvent.consume();
            });
        }

        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        protected double computePrefWidth(double height)
        {
            return super.computePrefWidth(height);
        }

        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        protected double computePrefHeight(double width)
        {
            return super.computePrefHeight(width);
        }

        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        @Override
        protected void layoutChildren()
        {
            double x = firstVisibleColumnOffset;
            double y = firstVisibleRowOffset;

            // We may not need the +1, but play safe:
            int newNumVisibleRows = Math.min(currentKnownRows - firstVisibleRowIndex, (int)Math.ceil(getHeight() / (rowHeight + GAP)) + 1);
            int newNumVisibleCols = 0;
            for (int column = firstVisibleColumnIndex; x < getWidth() && column < columnWidths.length; column++)
            {
                newNumVisibleCols += 1;
                x += columnWidths[column] + GAP;
            }
            VirtScrollStrTextGrid.this.visibleRowCount = newNumVisibleRows;
            VirtScrollStrTextGrid.this.visibleColumnCount = newNumVisibleCols;

            // Remove not-visible cells and put them in spare cells:
            for (Iterator<Entry<CellPosition, StructuredTextField>> iterator = visibleCells.entrySet().iterator(); iterator.hasNext(); )
            {
                Entry<CellPosition, StructuredTextField> vis = iterator.next();
                CellPosition pos = vis.getKey();
                boolean shouldBeVisible =
                    pos.rowIndex >= Math.max(0, firstVisibleRowIndex - extraRows) &&
                    pos.rowIndex < Math.min(currentKnownRows, firstVisibleRowIndex + visibleRowCount + 2 * extraRows) &&
                    pos.columnIndex >= firstVisibleColumnIndex &&
                    pos.columnIndex < firstVisibleColumnIndex + visibleColumnCount;
                if (!shouldBeVisible)
                {
                    spareCells.add(vis.getValue());
                    iterator.remove();
                }
            }

            y = firstVisibleRowOffset - Math.min(extraRows, firstVisibleRowIndex) * rowHeight;
            for (int rowIndex = Math.max(0, firstVisibleRowIndex - extraRows); rowIndex < Math.min(currentKnownRows, firstVisibleRowIndex + newNumVisibleRows + 2 * extraRows); rowIndex++)
            {
                x = firstVisibleColumnOffset;
                // If we need it, add another visible column
                for (int columnIndex = firstVisibleColumnIndex; columnIndex < firstVisibleColumnIndex + newNumVisibleCols; columnIndex++)
                {
                    CellPosition cellPosition = new CellPosition(rowIndex, columnIndex);
                    StructuredTextField cell = visibleCells.get(cellPosition);
                    // If cell isn't present, grab from spareCells:
                    if (cell == null)
                    {
                        if (!spareCells.isEmpty())
                        {
                            cell = spareCells.remove(spareCells.size() - 1);
                            // Reset state:
                            FXUtility.setPseudoclass(cell, "focused-cell", false);
                        }
                        else
                        {
                            cell = new StructuredTextField(() -> focusCell(cellPosition));
                            getChildren().add(cell);
                        }

                        visibleCells.put(cellPosition, cell);
                        StructuredTextField newCellFinal = cell;
                        // Blank then queue fetch:
                        cell.resetContent(new EditorKitSimpleLabel<>("Loading..."));
                        loadSave.fetchEditorKit(rowIndex, columnIndex, VirtScrollStrTextGrid.this);
                    }
                    cell.resizeRelocate(x, y, columnWidths[columnIndex], rowHeight);
                    x += columnWidths[columnIndex] + GAP;
                }
                y += rowHeight + GAP;
            }

            // Don't let spare cells be more than two visible rows or columns:
            int maxSpareCells = 2 * Math.max(newNumVisibleCols * 2, newNumVisibleRows * 2);

            while (spareCells.size() > maxSpareCells)
                container.getChildren().remove(spareCells.remove(spareCells.size() - 1));

            for (StructuredTextField spareCell : spareCells)
            {
                spareCell.relocate(10000, 10000);
            }
        }
    }

    public static class CellPosition
    {
        public final int rowIndex;
        public final int columnIndex;

        public CellPosition(int rowIndex, int columnIndex)
        {
            this.rowIndex = rowIndex;
            this.columnIndex = columnIndex;
        }

        @Override
        public boolean equals(@Nullable Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            CellPosition that = (CellPosition) o;

            if (rowIndex != that.rowIndex) return false;
            return columnIndex == that.columnIndex;
        }

        @Override
        public int hashCode()
        {
            int result = rowIndex;
            result = 31 * result + columnIndex;
            return result;
        }
    }
}
