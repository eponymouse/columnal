package records.gui.stable;

import com.google.common.collect.ImmutableList;
import javafx.animation.AnimationTimer;
import javafx.animation.Interpolator;
import javafx.application.Platform;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.MenuItem;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.error.InternalException;
import records.error.UserException;
import records.gui.stf.EditorKitSimpleLabel;
import records.gui.stf.StructuredTextField;
import records.gui.stf.StructuredTextField.EditorKit;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformFunction;
import utility.Pair;
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
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

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
public class VirtScrollStrTextGrid implements EditorKitCallback, ScrollBindable
{
    // Package visible to let sidebars access it
    static final double GAP = 1;
    static final int MAX_EXTRA_ROW_COLS = 12;

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

    // Cells which are spare: they are still members of the
    // parent pane to avoid costs of removing and re-adding,
    // but they are held at an off-screen position.
    private final ArrayList<StructuredTextField> spareCells;

    // Package visible to let sidebars access it
    final double rowHeight;
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

    // Package visible to let sidebars access it
    final Container container;
    // The items which are dependent on us.  Package-visible to allow sidebars to access it
    final Map<ScrollBindable, ScrollLock> scrollDependents = new IdentityHashMap<>();
    // How many extra rows to show off-screen each side, to account for scrolling (when actual display can lag logical display):
    private final IntegerProperty extraRows = new SimpleIntegerProperty(0);
    private final IntegerProperty extraCols = new SimpleIntegerProperty(0);

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

    // Immediately scrolls with no animation
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
        double rowPixelOffset = (topCell * (rowHeight + GAP)) - y;
        showAtOffset(new Pair<>(topCell, rowPixelOffset), null);
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
                showAtOffset(null, new Pair<>(col, -x));
                break;
            }
            x -= columnWidths[col] + GAP;
        }
    }

    // Focuses cell so that you can navigate around with keyboard
    public void focusCell(@Nullable CellPosition cellPosition)
    {
        visibleCells.forEach((visPos, visCell) -> {
            FXUtility.setPseudoclass(visCell, "focused-cell", visPos.equals(cellPosition));

        });
        focusedCell.set(cellPosition);
        if (cellPosition != null)
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

    // This scrolls just the layout, without smooth scrolling
    private void scrollLayoutXBy(double x)
    {
        scrollXToPixel(getCurrentScrollX() + x);
    }

    // This scrolls just the layout, without smooth scrolling
    // Returns the amount that we actually scrolled by, which will either
    // be given parameter, or otherwise it will have been clamped because we tried
    // to scroll at the very top or very bottom
    private double scrollLayoutYBy(double y)
    {
        double prevScroll = getCurrentScrollY();
        scrollYToPixel(prevScroll + y);
        return getCurrentScrollY() - prevScroll;
    }

    // Gets current scroll Y, where 0 is scrolled all the way to the top,
    // rowHeight+GAP means top of first row showing, etc
    private double getCurrentScrollY()
    {
        return firstVisibleRowIndex * (rowHeight + GAP) - firstVisibleRowOffset;
    }

    private double getCurrentScrollX()
    {
        double currentX = 0;
        for (int col = 0; col < firstVisibleColumnIndex; col++)
            currentX += columnWidths[col] + GAP;
        currentX -= firstVisibleColumnOffset;
        return currentX;
    }

    // This is the canonical scroll method which all scroll
    // attempts should pass through, to avoid duplicating the
    // update code
    @Override
    public void showAtOffset(@Nullable Pair<Integer, Double> rowAndPixelOffset, @Nullable Pair<Integer, Double> colAndPixelOffset)
    {
        if (rowAndPixelOffset != null)
        {
            int row = rowAndPixelOffset.getFirst();
            double rowPixelOffset = rowAndPixelOffset.getSecond();
            if (row <= 0)
            {
                row = 0;
                // Can't scroll above top of first item:
                rowPixelOffset = 0.0;
            }
            else if (row >= currentKnownRows - 1)
            {
                // Can't scroll beyond showing the last cell at the top of the window:
                row = currentKnownRows - 1;
                rowPixelOffset = 0;
            }
            this.firstVisibleRowOffset = rowPixelOffset;
            this.firstVisibleRowIndex = row;
        }
        if (colAndPixelOffset != null)
        {
            int col = colAndPixelOffset.getFirst();
            if (col < 0)
                col = 0;

            this.firstVisibleColumnOffset = colAndPixelOffset.getSecond();
            this.firstVisibleColumnIndex = col;
        }
        scrollDependents.forEach((grid, lock) -> {
            @Nullable Pair<Integer, Double> targetRow = null;
            @Nullable Pair<Integer, Double> targetCol = null;
            if (lock.includesVertical())
            {
                targetRow = new Pair<>(firstVisibleRowIndex, firstVisibleRowOffset);
            }
            
            if (lock.includesHorizontal())
            {
                targetCol = new Pair<>(firstVisibleColumnIndex, firstVisibleColumnOffset);
            }
            grid.showAtOffset(targetRow, targetCol);
        });
        updateKnownRows();
        container.requestLayout();
    }

    // Binds this item's scroll to src, so that when src changes, this does too.
    public void bindScroll(VirtScrollStrTextGrid src, ScrollLock lock)
    {
        // We actually keep track in src of dependents, not in dest of things we depend on
        src.scrollDependents.put(this, lock);

        if (lock.includesVertical())
        {
            scrollYToPixel(src.getCurrentScrollY());
            container.translateYProperty().bind(src.container.translateYProperty());
            extraRows.bind(src.extraRows);
        }

        if (lock.includesHorizontal())
        {
            scrollXToPixel(src.getCurrentScrollX());
            container.translateXProperty().bind(src.container.translateXProperty());
            extraCols.bind(src.extraCols);
        }
    }

    // Various package-visible items used by sidebars:
    int getFirstVisibleRowIndex() { return firstVisibleRowIndex; }
    double getFirstVisibleRowOffset() { return firstVisibleRowOffset; }
    int getFirstVisibleColIndex() { return firstVisibleColumnIndex; }
    double getFirstVisibleColOffset() { return firstVisibleColumnOffset; }
    int getCurrentKnownRows() { return currentKnownRows; }
    int getExtraRows() { return extraRows.get(); }
    int getNumColumns() { return columnWidths.length; }
    double getColumnWidth(int columnIndex) { return columnWidths[columnIndex]; }
    int getExtraCols() { return extraCols.get(); }

    public VirtRowLabels makeLineNumbers(FXPlatformFunction<Integer, List<MenuItem>> makeContextMenuItems)
    {
        return new VirtRowLabels(this, makeContextMenuItems);
    }

    public VirtColHeaders makeColumnHeaders(FXPlatformFunction<Integer, List<MenuItem>> makeContextMenuItems, FXPlatformFunction<Integer, ImmutableList<Node>> getHeaderContent)
    {
        return new VirtColHeaders(this, makeContextMenuItems, getHeaderContent);
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
        // Snap to top left:
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

    public void updateClip()
    {
        container.updateClip();
    }

    // Smooth scrolling bits:

    // AnimationTimer is run every frame, and so lets us do smooth scrolling:
    private @MonotonicNonNull AnimationTimer scroller;
    // Start time of current animation (scrolling again resets this) and target end time:
    private long scrollStartNanos;
    private long scrollEndNanos;
    // Always heading towards zero:
    private double scrollOffsetY;
    // Scroll offset at scrollStartNanos
    private double scrollStartOffsetY;
    private static final long SCROLL_TIME_NANOS = 300_000_000L;

    public void smoothScroll(ScrollEvent scrollEvent, ScrollLock axis)
    {
        if (scroller == null)
        {
            scroller = new AnimationTimer()
            {
                @Override
                public void handle(long now)
                {
                    // If scroll end time in future, and our target scroll is more than 1/8th pixel away:
                    if (scrollEndNanos > now && Math.abs(scrollOffsetY) > 0.125)
                    {
                        scrollOffsetY = Interpolator.EASE_BOTH.interpolate(scrollStartOffsetY, 0, (double)(now - scrollStartNanos) / (scrollEndNanos - scrollStartNanos));
                        container.setTranslateY(scrollOffsetY);
                    }
                    else
                    {
                        container.setTranslateY(0.0);
                        scrollOffsetY = 0.0;
                        extraRows.set(0);
                        stop();
                    }
                    container.updateClip();
                }
            };
        }

        // Reset start and end time:
        scrollStartNanos = System.nanoTime();
        scrollEndNanos = scrollStartNanos + SCROLL_TIME_NANOS;

        if (axis.includesHorizontal())
        {
            scrollLayoutXBy(-scrollEvent.getDeltaX());
        }

        if (axis.includesVertical())
        {
            // We subtract from current offset, because we may already be mid-scroll in which
            // case we don't want to jump, just want to add on (we will go faster to cover this
            // because scroll will be same duration but longer):
            scrollOffsetY += scrollLayoutYBy(-scrollEvent.getDeltaY());
            // Don't let offset get too large or we will need too many extra rows:
            if (Math.abs(scrollOffsetY) > MAX_EXTRA_ROW_COLS * (rowHeight + GAP))
            {
                // Jump to the destination:
                scrollOffsetY = 0;
            }
            scrollStartOffsetY = scrollOffsetY;
            extraRows.set((int) Math.ceil(Math.abs(scrollOffsetY) / (rowHeight + GAP)));
            container.setTranslateY(scrollOffsetY);
        }

        // Start the smooth scrolling animation:
        if (scrollOffsetY != 0.0)
            scroller.start();
    }

    // Package-visible to allow sidebars access
    @OnThread(Tag.FXPlatform)
    class Container extends Region
    {
        private final Rectangle clip;

        public Container()
        {
            getStyleClass().add("virt-grid");

            clip = new Rectangle();
            setClip(clip);


            addEventFilter(MouseEvent.ANY, mouseEvent -> {
                @Nullable CellPosition cellPosition = getCellPositionAt(mouseEvent.getX(), mouseEvent.getY());
                StructuredTextField cell = visibleCells.get(cellPosition);
                boolean positionIsFocusedCellWrapper = Objects.equals(focusedCell.get(), cellPosition);
                if (cell != null && cellPosition != null && mouseEvent.getClickCount() == 1 && !cell.isFocused() && !positionIsFocusedCellWrapper && mouseEvent.getEventType() == MouseEvent.MOUSE_CLICKED && mouseEvent.isStillSincePress())
                {
                    focusCell(cellPosition);
                }
                if (cell != null && cellPosition != null && mouseEvent.getClickCount() == 2 && !cell.isFocused() && isFocused() && positionIsFocusedCellWrapper && mouseEvent.getEventType() == MouseEvent.MOUSE_CLICKED && mouseEvent.isStillSincePress())
                {
                    cell.edit(new Point2D(mouseEvent.getSceneX(), mouseEvent.getSceneY()));
                }

                // We want to let event through if either:
                //   - we are already focusing the cell wrapper at that position
                //   - OR the STF itself is already focused.
                // Applying De Morgan's, mask the event if the cell wrapper isn't focused at that position
                // AND the STF is not focused
                if (cell != null && !cell.isFocused() && !(isFocused() && positionIsFocusedCellWrapper))
                    mouseEvent.consume();
            });

            // Filter because we want to steal it from the cells themselves:
            addEventFilter(ScrollEvent.ANY, scrollEvent -> {
                smoothScroll(scrollEvent, ScrollLock.BOTH);
                scrollEvent.consume();
            });

            FXUtility.addChangeListenerPlatformNN(focusedProperty(), focused -> {
                if (!focused)
                    focusCell(null);
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
                    pos.rowIndex >= Math.max(0, firstVisibleRowIndex - extraRows.get()) &&
                    pos.rowIndex < Math.min(currentKnownRows, firstVisibleRowIndex + visibleRowCount + 2 * extraRows.get()) &&
                    pos.columnIndex >= firstVisibleColumnIndex &&
                    pos.columnIndex < firstVisibleColumnIndex + visibleColumnCount;
                if (!shouldBeVisible)
                {
                    spareCells.add(vis.getValue());
                    iterator.remove();
                }
            }

            y = firstVisibleRowOffset - Math.min(extraRows.get(), firstVisibleRowIndex) * (rowHeight + GAP);
            for (int rowIndex = Math.max(0, firstVisibleRowIndex - extraRows.get()); rowIndex < Math.min(currentKnownRows, firstVisibleRowIndex + newNumVisibleRows + 2 * extraRows.get()); rowIndex++)
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
                            // TODO modify the relinquish when the cell is re-used:
                            cell = new StructuredTextField(() -> focusCell(cellPosition));
                            getChildren().add(cell);
                        }

                        visibleCells.put(cellPosition, cell);
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
            int maxSpareCells = MAX_EXTRA_ROW_COLS * Math.max(newNumVisibleCols, newNumVisibleRows);

            while (spareCells.size() > maxSpareCells)
                getChildren().remove(spareCells.remove(spareCells.size() - 1));

            for (StructuredTextField spareCell : spareCells)
            {
                spareCell.relocate(10000, 10000);
            }

            updateClip();
        }

        private void updateClip()
        {
            clip.setX(-getTranslateX());
            clip.setY(-getTranslateY());
            clip.setWidth(getWidth());
            clip.setHeight(getHeight());
            for (ScrollBindable scrollBindable : scrollDependents.keySet())
            {
                scrollBindable.updateClip();
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
