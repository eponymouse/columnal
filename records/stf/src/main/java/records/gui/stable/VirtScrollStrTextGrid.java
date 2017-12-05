package records.gui.stable;

import annotation.help.qual.UnknownIfHelp;
import annotation.qual.UnknownIfValue;
import annotation.userindex.qual.UnknownIfUserIndex;
import com.google.common.collect.ImmutableList;
import javafx.animation.AnimationTimer;
import javafx.animation.Interpolator;
import javafx.application.Platform;
import javafx.beans.binding.BooleanExpression;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollBar;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyCombination.Modifier;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.UnknownKeyFor;
import org.fxmisc.wellbehaved.event.EventPattern;
import org.fxmisc.wellbehaved.event.InputMap;
import org.fxmisc.wellbehaved.event.Nodes;
import records.error.InternalException;
import records.error.UserException;
import records.gui.stable.CellSelection.SelectionStatus;
import records.gui.stf.EditorKitSimpleLabel;
import records.gui.stf.StructuredTextField;
import records.gui.stf.StructuredTextField.EditorKit;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformBiConsumer;
import utility.FXPlatformConsumer;
import utility.FXPlatformFunction;
import utility.FXPlatformRunnable;
import utility.Pair;
import utility.SimulationFunction;
import utility.Utility;
import utility.Workers;
import utility.Workers.Priority;
import utility.gui.FXUtility;
import utility.gui.GUI;
import utility.gui.TranslationUtility;

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
    // Package visible to let sidebars access it:
    static final int MAX_EXTRA_ROW_COLS = 12;
    private final ScrollBar hBar;
    private final ScrollBar vBar;
    private final ScrollGroup scrollGroup;

    private boolean settingScrollBarVal = false;

    // Cells which are visible, organised as a 2D array
    // (inner is a row, outer is list of rows)
    private final Map<CellPosition, StructuredTextField> visibleCells;
    private final Map<Integer, Button> visibleRowAppendButtons;
    private final FXPlatformFunction<CellPosition, Boolean> canEdit;
    private final ObservableValue<Double> totalHeightEstimate;
    // The first index logically visible.  This is not actually necessarily the same
    // as first really-visible, if we are currently doing some smooth scrolling:
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
    private IntegerProperty currentKnownRows = new SimpleIntegerProperty(0);
    private boolean currentKnownRowsIsFinal = false;
    // A function to ask if the row index is valid.  If isRowValid(n)
    // returns false, it's guaranteed that isRowValid(m) for m>=n is also false.
    @OnThread(Tag.FXPlatform)
    private @Nullable SimulationFunction<Integer, Boolean> isRowValid;
    private double[] columnWidths;

    private final ObjectProperty<@Nullable CellSelection> selection = new SimpleObjectProperty<>(null);

    private final ValueLoadSave loadSave;

    // Package visible to let sidebars access it
    final Container container;
    // The items which are dependent on us.  Package-visible to allow sidebars to access it
    //final Map<ScrollBindable, ScrollLock> scrollDependents = new IdentityHashMap<>();
    // How many extra rows to show off-screen, to account for scrolling (when actual display can lag logical display).
    // Negative means we need them left/above, positive means we need them below/right:
    private final IntegerProperty extraRows = new SimpleIntegerProperty(0);
    private final IntegerProperty extraCols = new SimpleIntegerProperty(0);
    private @MonotonicNonNull Pair<VirtScrollStrTextGrid, ScrollLock> scrollLockedTo;
    private final SmoothScroller scrollX;
    private final SmoothScroller scrollY;
    private final Pane glass;
    private final StackPane stackPane;
    // null if you can't append to this grid:
    private @Nullable FXPlatformConsumer<Integer> appendRow;
    private @Nullable FXPlatformRunnable addColumn;
    private final BooleanProperty atLeftProperty = new SimpleBooleanProperty(false);
    private final BooleanProperty atRightProperty = new SimpleBooleanProperty(false);
    private final BooleanProperty atTopProperty = new SimpleBooleanProperty(false);
    private final BooleanProperty atBottomProperty = new SimpleBooleanProperty(false);
    private final DoubleProperty extraButtonWidthProperty = new SimpleDoubleProperty(0.0);

    public VirtScrollStrTextGrid(ValueLoadSave loadSave, FXPlatformFunction<CellPosition, Boolean> canEdit, ScrollBar hBar, ScrollBar vBar)
    {
        this.scrollGroup = (evt, _src) -> FXUtility.mouse(this).smoothScroll(evt, ScrollLock.BOTH);
        this.canEdit = canEdit;
        visibleCells = new HashMap<>();
        visibleRowAppendButtons = new HashMap<>();
        firstVisibleColumnIndex = 0;
        firstVisibleRowIndex = 0;
        firstVisibleColumnOffset = 0;
        firstVisibleRowOffset = 0;
        spareCells = new ArrayList<>();
        rowHeight = 24;
        columnWidths = new double[0];
        this.loadSave = loadSave;
        this.totalHeightEstimate = FXUtility.<@NonNull Number, Double>mapBindingEagerNN(currentKnownRows, rows -> rows.doubleValue() * rowHeight);

        container = new Container();
        glass = new Pane();
        glass.setMouseTransparent(true);
        glass.getStyleClass().add("virt-grid-glass");
        stackPane = new StackPane(container, glass);
        scrollX = new SmoothScroller(container.translateXProperty(), extraCols, FXUtility.mouse(this)::scrollLayoutXBy, targetX -> {
            // Count column widths in that direction until we reach target:
            double curX;
            int startCol;
            if (targetX < 0)
            {
                // If it's negative, we're scrolling left, and we need to show extra
                // rows to the right until they scroll out of view.
                double w = 0;
                for (startCol = firstVisibleColumnIndex; startCol < columnWidths.length; startCol++)
                {
                    w += columnWidths[startCol];
                    if (w >= container.getWidth())
                    {
                        break;
                    }
                }
                curX = w + firstVisibleColumnOffset - container.getWidth();
                int col;
                for (col = startCol + 1; curX < -targetX; col++)
                {
                    if (col >= columnWidths.length)
                        return columnWidths.length - startCol;
                    curX += columnWidths[col];
                }
                // Will be 0 or positive:
                return col - startCol;
            }
            else
            {
                // Opposite: scrolling right, need extra rows left until scroll out of view:
                startCol = firstVisibleColumnIndex;
                int col;
                curX = firstVisibleColumnOffset;
                for (col = startCol - 1; curX > -targetX; col--)
                {
                    if (col < 0)
                        return -startCol;
                    curX -= columnWidths[col];
                }
                // Will be 0 or negative:
                return col - startCol;
            }
        });
        scrollY = new SmoothScroller(container.translateYProperty(), extraRows, FXUtility.mouse(this)::scrollLayoutYBy, y -> (int)(Math.signum(-y) * Math.ceil(Math.abs(y) / rowHeight)));

        this.hBar = hBar;
        hBar.setMin(0.0);
        hBar.setMax(1.0);
        hBar.setValue(0.0);
        hBar.valueProperty().addListener(new ChangeListener<Number>()
        {
            @Override
            @SuppressWarnings("nullness")
            @OnThread(value = Tag.FXPlatform, ignoreParent = true)
            public void changed(ObservableValue<? extends Number> prop, Number oldScrollBarVal, Number newScrollBarVal)
            {
                if (!settingScrollBarVal)
                {
                    double delta = getMaxScrollX() * (newScrollBarVal.doubleValue() - oldScrollBarVal.doubleValue());
                    scrollX.smoothScroll(delta);
                }
            }
        });

        this.vBar = vBar;
        vBar.setMin(0.0);
        vBar.setMax(1.0);
        vBar.setValue(0.0);
        vBar.valueProperty().addListener(new ChangeListener<Number>()
        {
            @Override
            @SuppressWarnings("nullness")
            @OnThread(value = Tag.FXPlatform, ignoreParent = true)
            public void changed(ObservableValue<? extends Number> prop, Number oldScrollBarVal, Number newScrollBarVal)
            {
                if (!settingScrollBarVal)
                {
                    double delta = getMaxScrollY() * (newScrollBarVal.doubleValue() - oldScrollBarVal.doubleValue());
                    scrollY.smoothScroll(delta);
                }
            }
        });
    }

    private double getMaxScrollX()
    {
        return Math.max(0, sumColumnWidths(0, columnWidths.length) + extraButtonWidthProperty.get() - container.getWidth());
    }

    private double getMaxScrollY()
    {
        return Math.max(0, (currentKnownRows.get() - 1) * rowHeight - container.getHeight());
    }

    public ObjectExpression<@Nullable CellSelection> selectionProperty()
    {
        return selection;
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
        int topCell = (int) Math.floor(y / rowHeight);
        double rowPixelOffset = (topCell * rowHeight) - y;

        int bottomCell = (int)Math.floor((y + container.getHeight()) / rowHeight);
        int rows = currentKnownRows.get() + (appendRow == null ? 0 : 1);
        if (bottomCell >= rows)
        {
            topCell = rows - 1 - (int)Math.floor(container.getHeight() / rowHeight);
            rowPixelOffset = container.getHeight() - (rows - topCell) * rowHeight;
        }

        Pair<Integer, Double> scrollDest = new Pair<>(topCell, rowPixelOffset);
        showAtOffset(scrollDest, null);
    }

    private void updateKnownRows()
    {
        if (currentKnownRowsIsFinal)
        {
            // We already reached the end:
            return;
        }

        final int prevKnownRows = currentKnownRows.get();
        if (prevKnownRows > firstVisibleRowIndex + visibleRowCount + 200)
        {
            // If there's more than 200 unshown, that will do as an estimate:
            return;
        }

        int searchMax = firstVisibleRowIndex + visibleRowCount + 250;
        @Nullable SimulationFunction<Integer, Boolean> isRowValidFinal = this.isRowValid;
        Workers.onWorkerThread("Calculating number of rows", Priority.FETCH, () -> {
            int knownRows;
            boolean reachedEnd = false;
            // If we have say knownRows = 5, that's the total number, but last valid row is 4
            // So we start by checking if row 5 (same as knownRows) is valid, which would
            // actually be one more row than before.
            for (knownRows = prevKnownRows; knownRows < searchMax; knownRows++)
            {
                try
                {
                    if (isRowValidFinal == null)
                        break;
                    if (!isRowValidFinal.apply(knownRows))
                    {
                        reachedEnd = true;
                        break;
                    }
                }
                catch (InternalException | UserException e)
                {
                    Utility.log(e);
                    break;
                }
            }
            int knownRowsFinal = knownRows;
            boolean reachedEndFinal = reachedEnd;
            Platform.runLater(() -> {
                currentKnownRows.set(knownRowsFinal);
                currentKnownRowsIsFinal = reachedEndFinal;
                container.requestLayout();
            });
        });
    }

    public void scrollXToPixel(double targetX)
    {
        double lhs = Math.max(targetX, 0.0);
        for (int lhsCol = 0; lhsCol < columnWidths.length; lhsCol++)
        {
            if (lhs < columnWidths[lhsCol])
            {
                // Stop here, possibly clamping to RHS if needed:
                double clampedLHS = container.getWidth() - extraButtonWidthProperty.get();
                for (int clampedLHSCol = columnWidths.length - 1; clampedLHSCol >= 0; clampedLHSCol--)
                {
                    if (clampedLHS < columnWidths[lhsCol])
                    {
                        // We've found furthest place we could
                        // scroll to, so go to the leftmost out of
                        // furthest place, and ideal place:

                        // clampedLHS was from right, make it from left:
                        clampedLHS = columnWidths[lhsCol] - clampedLHS;
                        if (clampedLHSCol < lhsCol || (clampedLHSCol == lhsCol && clampedLHS < lhs))
                            showAtOffset(null, new Pair<>(clampedLHSCol, -clampedLHS));
                        else
                            showAtOffset(null, new Pair<>(lhsCol, -lhs));
                        return;
                    }
                    clampedLHS -= columnWidths[clampedLHSCol];
                }
                // If we get here, column widths are smaller than
                // container, so stay at left:
                showAtOffset(null, new Pair<>(0, 0.0));
                return;
            }
            lhs -= columnWidths[lhsCol];
        }
    }

    // Focuses cell so that you can navigate around with keyboard
    public void select(@Nullable CellSelection cellSelection)
    {
        visibleCells.forEach((visPos, visCell) -> {
            SelectionStatus status = cellSelection == null ? SelectionStatus.UNSELECTED : cellSelection.selectionStatus(visPos);
            FXUtility.setPseudoclass(visCell, "primary-selected-cell", status == SelectionStatus.PRIMARY_SELECTION);
            FXUtility.setPseudoclass(visCell, "secondary-selected-cell", status == SelectionStatus.SECONDARY_SELECTION);
        });
        selection.set(cellSelection);
        if (cellSelection != null)
            container.requestFocus();
    }

    // Edits cell so that you can start typing content
    public void editCell(CellSelection cellPosition)
    {
        CellPosition editPosition = cellPosition.editPosition();
        StructuredTextField cell = visibleCells.get(editPosition);
        if (cell != null && canEdit.apply(editPosition))
        {
            cell.requestFocus();
        }
    }

    public boolean isEditingCell(int rowIndex, int columnIndex)
    {
        StructuredTextField cell = visibleCells.get(new CellPosition(rowIndex, columnIndex));
        return cell != null && cell.isFocused();
    }

    // This scrolls just the layout, without smooth scrolling
    private double scrollLayoutXBy(double x)
    {
        double prevScroll = getCurrentScrollX();
        scrollXToPixel(getCurrentScrollX() + x);
        return getCurrentScrollX() - prevScroll;
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
        return firstVisibleRowIndex * rowHeight - firstVisibleRowOffset;
    }

    private double getCurrentScrollX()
    {
        return sumColumnWidths(0, firstVisibleColumnIndex) - firstVisibleColumnOffset;
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
            if (row < 0)
            {
                row = 0;
                // Can't scroll above top of first item:
                rowPixelOffset = 0.0;
            }
            else if (row > Math.max(0, currentKnownRows.get() - 1))
            {
                // Can't scroll beyond showing the last cell at the top of the window:
                row = Math.max(0, currentKnownRows.get() - 1);
                rowPixelOffset = 0;
            }
            this.firstVisibleRowOffset = rowPixelOffset;
            this.firstVisibleRowIndex = row;
            updateVBar();

            boolean atTop = firstVisibleRowIndex == 0 && firstVisibleRowOffset >= -5;
            FXUtility.setPseudoclass(glass, "top-shadow", !atTop);
        }
        if (colAndPixelOffset != null)
        {
            int col = colAndPixelOffset.getFirst();
            if (col < 0)
                col = 0;

            this.firstVisibleColumnOffset = colAndPixelOffset.getSecond();
            this.firstVisibleColumnIndex = col;
            updateHBar();

            boolean atLeft = firstVisibleColumnIndex == 0 && firstVisibleColumnOffset >= -5;
            FXUtility.setPseudoclass(glass, "left-shadow", !atLeft);
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

    private void updateVBar()
    {
        settingScrollBarVal = true;
        double maxScrollY = getMaxScrollY();
        double currentScrollY = getCurrentScrollY();
        vBar.setValue(maxScrollY < 1.0 ? 0.0 : (currentScrollY / maxScrollY));
        vBar.setVisibleAmount(maxScrollY < 1.0 ? 1.0 : (container.getHeight() / (maxScrollY + container.getHeight())));
        vBar.setMax(maxScrollY < 1.0 ? 0.0 : 1.0);
        atTopProperty.set(currentScrollY < 1.0);
        atBottomProperty.set(currentScrollY >= maxScrollY - 1.0);

        settingScrollBarVal = false;
    }

    private void updateHBar()
    {
        settingScrollBarVal = true;
        double maxScrollX = getMaxScrollX();
        double currentScrollX = getCurrentScrollX();
        hBar.setValue(maxScrollX < 1.0 ? 0.0 : (currentScrollX / maxScrollX));
        hBar.setVisibleAmount(maxScrollX < 1.0 ? 1.0 : (container.getWidth() / (maxScrollX + container.getWidth())));
        hBar.setMax(maxScrollX < 1.0 ? 0.0 : 1.0);
        atLeftProperty.set(currentScrollX < 1.0);
        atRightProperty.set(currentScrollX >= maxScrollX - 1.0);
        settingScrollBarVal = false;
    }

    // Binds this item's scroll to src, so that when src changes, this does too.
    public void bindScroll(VirtScrollStrTextGrid src, ScrollLock lock)
    {
        // We actually primarily keep track in src of dependents, not in dest of things we depend on
        src.scrollDependents.put(this, lock);
        // But we also keep track ourselves:
        scrollLockedTo = new Pair<>(src, lock);

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
    int getFirstVisibleRowIndex()
    {
        return firstVisibleRowIndex;
    }

    double getFirstVisibleRowOffset()
    {
        return firstVisibleRowOffset;
    }

    int getFirstVisibleColIndex()
    {
        return firstVisibleColumnIndex;
    }

    double getFirstVisibleColOffset()
    {
        return firstVisibleColumnOffset;
    }

    int getCurrentKnownRows()
    {
        return currentKnownRows.get();
    }

    int getExtraRows()
    {
        return extraRows.get();
    }

    int getNumColumns()
    {
        return columnWidths.length;
    }

    double getColumnWidth(int columnIndex)
    {
        return columnWidths[columnIndex];
    }

    int getExtraCols()
    {
        return extraCols.get();
    }

    public VirtRowLabels makeLineNumbers(FXPlatformFunction<Integer, List<MenuItem>> makeContextMenuItems)
    {
        return new VirtRowLabels(this, makeContextMenuItems);
    }

    public VirtColHeaders makeColumnHeaders(FXPlatformFunction<Integer, List<MenuItem>> makeContextMenuItems, FXPlatformFunction<Integer, ImmutableList<Node>> getHeaderContent)
    {
        VirtColHeaders virtColHeaders = new VirtColHeaders(this, makeContextMenuItems, getHeaderContent);
        extraButtonWidthProperty.bind(virtColHeaders.addColumnButtonWidthProperty());
        return virtColHeaders;
    }

    /**
     * Adds the column widths for any column index C where startColIndexIncl <= C < endColIndexExcl
     * If startColIndexIncl >= endColIndexExcl, zero will be returned.  Each column width includes GAP.
     */
    double sumColumnWidths(int startColIndexIncl, int endColIndexExcl)
    {
        double total = 0;
        for (int i = startColIndexIncl; i < endColIndexExcl; i++)
        {
            total += getColumnWidth(i);
        }
        return total;
    }

    public ObservableValue<Double> totalHeightEstimateProperty()
    {
        return totalHeightEstimate;
    }

    public void setColumnWidth(int columnIndex, double width)
    {
        if (columnIndex >= 0 && columnIndex < columnWidths.length)
        {
            columnWidths[columnIndex] = width;
            columnWidthChanged(columnIndex, width);
        }
    }

    @Override
    public void columnWidthChanged(int columnIndex, double newWidth)
    {
        // Fix hBar:
        updateHBar();

        container.requestLayout();
        for (ScrollBindable scrollBindable : scrollDependents.keySet())
        {
            scrollBindable.columnWidthChanged(columnIndex, newWidth);
        }
    }

    @Override
    public @OnThread(Tag.FXPlatform) void columnsChanged()
    {
        //Not sure we care that what we are bound to has had its columns changed.
    }

    public int _test_getFirstLogicalVisibleRowIncl()
    {
        // This is first row with middle visible, so check for that:
        if (firstVisibleRowOffset < -rowHeight / 2.0)
            return firstVisibleRowIndex + 1;
        else
            return firstVisibleRowIndex;
    }

    public int _test_getLastLogicalVisibleRowExcl()
    {
        int numVisible = (int)Math.round((double)container.getHeight() / rowHeight);
        return firstVisibleRowIndex + numVisible;
    }

    public ObservableValue<Number> currentKnownRows()
    {
        return currentKnownRows;
    }

    public ReadOnlyDoubleProperty heightProperty()
    {
        return container.heightProperty();
    }

    public void removedRows(int startRow, int removedRowsCount)
    {
        HashMap<CellPosition, StructuredTextField> oldVis = new HashMap<>(visibleCells);
        visibleCells.clear();
        oldVis.forEach((pos, stf) -> {
            if (pos.rowIndex < startRow)
                visibleCells.put(pos, stf);
            else if (pos.rowIndex < startRow + removedRowsCount)
                spareCells.add(stf);
            else
                visibleCells.put(new CellPosition(pos.rowIndex - removedRowsCount, pos.columnIndex), stf);
        });
        currentKnownRows.set(currentKnownRows.getValue() - removedRowsCount);
        container.requestLayout();
    }

    public void addedRows(int startRow, int addedRowsCount)
    {
        HashMap<CellPosition, StructuredTextField> oldVis = new HashMap<>(visibleCells);
        visibleCells.clear();
        oldVis.forEach((pos, stf) -> {
            if (pos.rowIndex < startRow)
                visibleCells.put(pos, stf);
            else
                visibleCells.put(new CellPosition(pos.rowIndex + addedRowsCount, pos.columnIndex), stf);
        });
        currentKnownRows.set(currentKnownRows.getValue() + addedRowsCount);
        container.requestLayout();
    }

    public @Nullable FXPlatformRunnable getAddColumn()
    {
        return addColumn;
    }

    // True when the grid is scrolled all the way to the left
    public BooleanExpression atLeftProperty()
    {
        return atLeftProperty;
    }

    // True when the grid is scrolled all the way to the right
    public BooleanExpression atRightProperty()
    {
        return atRightProperty;
    }

    // True when the grid is scrolled all the way to the top
    public BooleanExpression atTopProperty()
    {
        return atTopProperty;
    }

    // True when the grid is scrolled all the way to the bottom
    public BooleanExpression atBottomProperty()
    {
        return atBottomProperty;
    }

    public void selectRow(int rowIndex)
    {
        select(new RowSelection(rowIndex, rowIndex));
    }

    public void selectColumn(int columnIndex)
    {
        select(new ColumnSelection(columnIndex, columnIndex));
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
        void fetchEditorKit(int rowIndex, int colIndex, FXPlatformConsumer<CellPosition> relinquishFocus, EditorKitCallback setEditorKit);
    }

    public void setData(SimulationFunction<Integer, Boolean> isRowValid, @Nullable FXPlatformRunnable addColumn, @Nullable FXPlatformConsumer<Integer> appendRow, double[] columnWidths)
    {
        // Snap to top left:
        firstVisibleRowIndex = 0;
        firstVisibleColumnIndex = 0;
        firstVisibleColumnOffset = 0;
        firstVisibleRowOffset = 0;
        settingScrollBarVal = true;
        hBar.setValue(0.0);
        vBar.setValue(0.0);
        hBar.setVisibleAmount(1.0);
        vBar.setVisibleAmount(1.0);
        settingScrollBarVal = false;

        // Empty previous:
        spareCells.addAll(visibleCells.values());
        visibleCells.clear();

        // These variables resize the number of elements,
        // and then layout actually rejigs the display:
        this.isRowValid = isRowValid;
        this.appendRow = appendRow;
        this.addColumn = addColumn;
        this.currentKnownRows.set(0);
        this.currentKnownRowsIsFinal = false;
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

    private @Nullable RectangularCellSelection getCellPositionAt(double x, double y)
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
        rowIndex = (int) Math.floor(y / rowHeight) + firstVisibleRowIndex;
        if (rowIndex >= currentKnownRows.get())
            return null;
        return new RectangularCellSelection(rowIndex, colIndex);
    }

    public Region getNode()
    {
        return stackPane;
    }

    public void updateClip()
    {
        container.updateClip();
    }


    public void smoothScroll(ScrollEvent scrollEvent, ScrollLock axis)
    {
        smoothScroll(axis.includesHorizontal() ? -scrollEvent.getDeltaX() : 0.0, axis.includesVertical() ? -scrollEvent.getDeltaY() : 0.0);
    }

    public void smoothScroll(double deltaX, double deltaY)
    {
        if (deltaX != 0.0)
            scrollX.smoothScroll(deltaX);
        if (deltaY != 0.0)
            scrollY.smoothScroll(deltaY);
    }

    // Smooth scrolling class.  Handles smooth scrolling on a single axis:
    @OnThread(Tag.FXPlatform)
    class SmoothScroller
    {
        // AnimationTimer is run every frame, and so lets us do smooth scrolling:
        private @MonotonicNonNull AnimationTimer scroller;
        // Start time of current animation (scrolling again resets this) and target end time:
        private long scrollStartNanos;
        private long scrollEndNanos;
        // Always heading towards zero.  If it's negative, then the user is scrolling left/up,
        // and thus we are currently that many pixels right/below of where it really should be,
        // and then the animation will animate the content left/up.  If it's positive, invert all that.
        private double scrollOffset;
        // Scroll offset at scrollStartNanos
        private double scrollStartOffset;
        private static final long SCROLL_TIME_NANOS = 300_000_000L;

        // The translateX/translateY of container, depending on which axis we are:
        private final DoubleProperty translateProperty;
        // extraRows/extraCols, depending on which axis we are:
        private final IntegerProperty extraRowCols;
        // Reference to scrollLayoutXBy/scrollLayoutYBy, depending on axis:
        private final FXPlatformFunction<Double, Double> scrollLayoutBy;
        // Given a scroll offset, works out how many extra rows/cols we need:
        private final FXPlatformFunction<Double, Integer> calcExtraRowCols;

        SmoothScroller(DoubleProperty translateProperty, IntegerProperty extraRowCols, FXPlatformFunction<Double, Double> scrollLayoutBy, FXPlatformFunction<Double, Integer> calcExtraRowCols)
        {
            this.translateProperty = translateProperty;
            this.extraRowCols = extraRowCols;
            this.scrollLayoutBy = scrollLayoutBy;
            this.calcExtraRowCols = calcExtraRowCols;
        }

        public void smoothScroll(double delta)
        {
            if (scroller == null)
            {
                scroller = new AnimationTimer()
                {
                    @Override
                    public void handle(long now)
                    {
                        // If scroll end time in future, and our target scroll is more than 1/8th pixel away:
                        if (scrollEndNanos > now && Math.abs(scrollOffset) > 0.125)
                        {
                            scrollOffset = Interpolator.EASE_BOTH.interpolate(scrollStartOffset, 0, (double) (now - scrollStartNanos) / (scrollEndNanos - scrollStartNanos));
                            translateProperty.set(scrollOffset);
                        }
                        else
                        {
                            translateProperty.set(0.0);
                            scrollOffset = 0.0;
                            extraRowCols.set(0);
                            stop();
                        }
                        container.updateClip();
                    }
                };
            }

            // Reset start and end time:
            long now = System.nanoTime();
            boolean justStarted = scrollEndNanos < now;
            if (justStarted)
                scrollStartNanos = now;
            scrollEndNanos = now + SCROLL_TIME_NANOS;

            if (delta != 0.0)
            {
                // We subtract from current offset, because we may already be mid-scroll in which
                // case we don't want to jump, just want to add on (we will go faster to cover this
                // because scroll will be same duration but longer):
                scrollOffset += scrollLayoutBy.apply(delta);
                int extra = calcExtraRowCols.apply(scrollOffset);
                // Don't let offset get too large or we will need too many extra rows:
                if (Math.abs(extra) > MAX_EXTRA_ROW_COLS)
                {
                    // Jump to the destination:
                    scrollOffset = 0;
                    extraRowCols.set(0);
                }
                else
                {
                    extraRowCols.set(extra);
                }
                if (justStarted)
                    scrollStartOffset = scrollOffset;
                translateProperty.set(scrollOffset);
            }

            // Start the smooth scrolling animation:
            if (scrollOffset != 0.0)
                scroller.start();
        }
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

            FXUtility.addChangeListenerPlatformNN(widthProperty(), w -> updateHBar());
            FXUtility.addChangeListenerPlatformNN(heightProperty(), h -> updateVBar());


            EventHandler<? super @UnknownKeyFor @UnknownIfValue @UnknownIfUserIndex @UnknownIfHelp MouseEvent> clickHandler = mouseEvent -> {

                @Nullable CellSelection cellPosition = getCellPositionAt(mouseEvent.getX(), mouseEvent.getY());
                @Nullable StructuredTextField cell = cellPosition == null ? null : visibleCells.get(cellPosition.editPosition());
                // This check may not be right now we have selections larger than one cell
                boolean positionIsFocusedCellWrapper = Objects.equals(selection.get(), cellPosition);
                if (cell != null && cellPosition != null && mouseEvent.getClickCount() == 1 && !cell.isFocused() && !positionIsFocusedCellWrapper && mouseEvent.getEventType() == MouseEvent.MOUSE_CLICKED && mouseEvent.isStillSincePress())
                {
                    select(cellPosition);
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
            };
            addEventFilter(MouseEvent.MOUSE_CLICKED, clickHandler);
            addEventFilter(MouseEvent.MOUSE_PRESSED, clickHandler);
            addEventFilter(MouseEvent.MOUSE_RELEASED, clickHandler);

                // Filter because we want to steal it from the cells themselves:
            addEventFilter(ScrollEvent.ANY, scrollEvent -> {
                //smoothScroll(scrollEvent, ScrollLock.BOTH);
                scrollGroup.scroll(scrollEvent, VirtScrollStrTextGrid.this);
                scrollEvent.consume();
            });

            FXUtility.addChangeListenerPlatformNN(focusedProperty(), focused -> {
                if (!focused)
                    select(null);
            });

            Nodes.addInputMap(FXUtility.keyboard(this), InputMap.sequence(
                bindS(KeyCode.HOME, (shift, c) -> c.home(shift)),
                bindS(KeyCode.END, (shift, c) -> c.end(shift)),
                bindS(KeyCode.UP, (shift, c) -> c.move(shift, -1, 0)),
                bindS(KeyCode.DOWN, (shift, c) -> c.move(shift, 1, 0)),
                bindS(KeyCode.LEFT, (shift, c) -> c.move(shift, 0, -1)),
                bindS(KeyCode.RIGHT, (shift, c) -> c.move(shift, 0, 1)),
                bindS(KeyCode.PAGE_UP, (shift, c) -> c.move(shift, -((int)Math.floor(c.getHeight() / rowHeight) - 1), 0)),
                bindS(KeyCode.PAGE_DOWN, (shift, c) -> c.move(shift, (int)Math.floor(c.getHeight() / rowHeight) - 1, 0)),
                InputMap.<Event, KeyEvent>consume(EventPattern.keyPressed(KeyCode.ENTER), e -> {
                    @Nullable CellSelection focusedCellPosition = selection.get();
                    if (focusedCellPosition != null)
                    {
                        editCell(focusedCellPosition);
                    }
                    e.consume();
                }),
                InputMap.<Event, KeyEvent>consume(EventPattern.keyPressed(KeyCode.SPACE), e -> {
                    @Nullable CellSelection focusedCellPosition = selection.get();
                    if (focusedCellPosition != null)
                    {
                        editCell(focusedCellPosition);
                    }
                    e.consume();
                })
            ));
        }

        private void move(boolean extendSelection, int rows, int columns)
        {
            @Nullable CellSelection focusedCellPos = selection.get();
            if (focusedCellPos != null)
            {
                select(focusedCellPos.move(extendSelection, rows, columns, currentKnownRows.get(), getNumColumns()));
            }
        }

        private InputMap<KeyEvent> bind(@UnknownInitialization(Region.class) Container this, KeyCode keyCode, FXPlatformConsumer<Container> action, Modifier... modifiers)
        {
            return InputMap.<Event, KeyEvent>consume(EventPattern.keyPressed(keyCode, modifiers), e -> {
                action.consume(FXUtility.keyboard(this));
                e.consume();
            });
        }

        /**
         * Like bind, but binds with and without shift held, passing a boolean for shift state to the lambda
         * @param keyCode
         * @param action
         * @return
         */
        private InputMap<KeyEvent> bindS(@UnknownInitialization(Region.class) Container this, KeyCode keyCode, FXPlatformBiConsumer<Boolean, Container> action)
        {
            return InputMap.<Event, KeyEvent>consume(EventPattern.keyPressed(keyCode, KeyCombination.SHIFT_ANY), e -> {
                action.consume(e.isShiftDown(), FXUtility.keyboard(this));
                e.consume();
            });
        }

        private void home(boolean extendSelection)
        {
            @Nullable CellSelection focusedCellPos = selection.get();
            scrollYToPixel(0.0);
            // Force layout before attempting to focus cell as it may not have been visible:
            layout();
            if (focusedCellPos != null)
            {
                select(focusedCellPos.atHome(extendSelection));
            }
        }

        private void end(boolean extendSelection)
        {
            @Nullable CellSelection focusedCellPos = selection.get();
            scrollYToPixel(Double.MAX_VALUE);
            // Force layout before attempting to focus cell as it may not have been visible:
            layout();
            if (focusedCellPos != null)
            {
                select(focusedCellPos.atEnd(extendSelection, currentKnownRows.get(), getNumColumns()));
            }
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
            int newNumVisibleRows = Math.min(currentKnownRows.get() - firstVisibleRowIndex, (int)Math.ceil(getHeight() / rowHeight) + 1);
            int newNumVisibleCols = 0;
            for (int column = firstVisibleColumnIndex; x < getWidth() && column < columnWidths.length; column++)
            {
                newNumVisibleCols += 1;
                x += columnWidths[column];
            }
            VirtScrollStrTextGrid.this.visibleRowCount = newNumVisibleRows;
            VirtScrollStrTextGrid.this.visibleColumnCount = newNumVisibleCols;

            // This includes extra rows needed for smooth scrolling:
            int firstDisplayRow = getFirstDisplayRow();
            int lastDisplayRowExcl = getLastDisplayRowExcl();
            int firstDisplayCol = getFirstDisplayCol();
            int lastDisplayColExcl = getLastDisplayColExcl();

            // Remove not-visible cells and put them in spare cells:
            for (Iterator<Entry<CellPosition, StructuredTextField>> iterator = visibleCells.entrySet().iterator(); iterator.hasNext(); )
            {
                Entry<CellPosition, StructuredTextField> vis = iterator.next();
                CellPosition pos = vis.getKey();

                boolean shouldBeVisible =
                    pos.rowIndex >= firstDisplayRow &&
                    pos.rowIndex < lastDisplayRowExcl &&
                    pos.columnIndex >= firstDisplayCol &&
                    pos.columnIndex < lastDisplayColExcl;
                if (!shouldBeVisible)
                {
                    spareCells.add(vis.getValue());
                    iterator.remove();
                }
            }

            y = firstVisibleRowOffset - (firstVisibleRowIndex - firstDisplayRow) * rowHeight;
            for (int rowIndex = firstDisplayRow; rowIndex < lastDisplayRowExcl; rowIndex++)
            {
                x = firstVisibleColumnOffset - sumColumnWidths(firstDisplayCol, firstVisibleColumnIndex);
                // If we need it, add another visible column
                for (int columnIndex = firstDisplayCol; columnIndex < lastDisplayColExcl; columnIndex++)
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
                            FXUtility.setPseudoclass(cell, "primary-selected-cell", false);
                            FXUtility.setPseudoclass(cell, "secondary-selected-cell", false);
                        }
                        else
                        {
                            cell = new StructuredTextField();
                            cell.getStyleClass().add("virt-grid-cell");
                            StructuredTextField cellFinal = cell;
                            FXUtility.addChangeListenerPlatformNN(cell.focusedProperty(), b -> cellFocusChanged(cellFinal, b));
                            getChildren().add(cell);
                        }

                        visibleCells.put(cellPosition, cell);
                        // Blank then queue fetch:
                        cell.resetContent(new EditorKitSimpleLabel<>(TranslationUtility.getString("data.loading")));
                        loadSave.fetchEditorKit(rowIndex, columnIndex, c -> select(new RectangularCellSelection(c.rowIndex, c.columnIndex)), VirtScrollStrTextGrid.this);
                    }
                    cell.setVisible(true);
                    cell.resizeRelocate(x, y, columnWidths[columnIndex], rowHeight);
                    x += columnWidths[columnIndex];
                }
                y += rowHeight;
            }
            if (y < getHeight() && appendRow != null && currentKnownRowsIsFinal && currentKnownRows.get() == lastDisplayRowExcl)
            {
                x = firstVisibleColumnOffset - sumColumnWidths(firstDisplayCol, firstVisibleColumnIndex);
                for (int columnIndex = firstDisplayCol; columnIndex < lastDisplayColExcl; columnIndex++)
                {
                    Button appendButton = visibleRowAppendButtons.computeIfAbsent(columnIndex, i -> {
                        Button newButton = GUI.button("stableView.append", () -> {
                            // Shouldn't be given outer check, but this one is for the null checker:
                            if (appendRow != null)
                            {
                                appendRow.consume(currentKnownRows.get());
                                currentKnownRowsIsFinal = false;
                                updateKnownRows();
                            }
                        }, "stable-view-row-append-button");
                        getChildren().add(newButton);
                        return newButton;
                    });
                    appendButton.setVisible(true);
                    appendButton.resizeRelocate(x, y, columnWidths[columnIndex], rowHeight);
                    // TODO add the highlight-all-buttons-on-hover effect
                    x += columnWidths[columnIndex];
                }
                visibleRowAppendButtons.forEach((col, button) -> {
                    if (col < firstDisplayCol || col >= lastDisplayColExcl)
                    {
                        button.relocate(-1000, -1000);
                        button.setVisible(false);
                    }
                });
            }
            else
            {
                for (Button button : visibleRowAppendButtons.values())
                {
                    button.relocate(-1000, -1000);
                    button.setVisible(false);
                }
            }
            // TODO remove unused append row buttons

            // Don't let spare cells be more than two visible rows or columns:
            int maxSpareCells = MAX_EXTRA_ROW_COLS * Math.max(newNumVisibleCols, newNumVisibleRows);

            while (spareCells.size() > maxSpareCells)
                getChildren().remove(spareCells.remove(spareCells.size() - 1));

            for (StructuredTextField spareCell : spareCells)
            {
                spareCell.relocate(-1000, -1000);
                spareCell.setVisible(false);
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

    private void cellFocusChanged(StructuredTextField cell, boolean focused)
    {
        @MonotonicNonNull CellPosition cellPosition = null;
        for (Entry<CellPosition, StructuredTextField> vis : visibleCells.entrySet())
        {
            if (vis.getValue().equals(cell))
            {
                cellPosition = vis.getKey();
                break;
            }
        }
        @Nullable CellPosition cellPositionFinal = cellPosition;
        visibleCells.forEach((visPos, visCell) -> {
            boolean correctRow = cellPositionFinal != null && cellPositionFinal.rowIndex == visPos.rowIndex;
            boolean correctColumn = cellPositionFinal != null && cellPositionFinal.columnIndex == visPos.columnIndex;
            // XOR; don't want to set it for actually focused cell:
            boolean showAsCross = correctRow != correctColumn;
            FXUtility.setPseudoclass(visCell, "editing-row-col", focused && showAsCross);

        });
    }

    // The last actual display column (exclusive), including any needed for displaying smooth scrolling
    int getLastDisplayColExcl()
    {
        return Math.min(columnWidths.length, firstVisibleColumnIndex + visibleColumnCount + Math.max(0, extraCols.get()));
    }

    // The first actual display column, including any needed for displaying smooth scrolling
    int getFirstDisplayCol()
    {
        return Math.max(0, firstVisibleColumnIndex + Math.min(0, extraCols.get()));
    }

    // The last actual display row (exclusive), including any needed for displaying smooth scrolling
    int getLastDisplayRowExcl()
    {
        return Math.min(currentKnownRows.get(), firstVisibleRowIndex + visibleRowCount + Math.max(0, extraRows.get()));
    }

    // The first actual display row, including any needed for displaying smooth scrolling
    int getFirstDisplayRow()
    {
        return Math.max(0, firstVisibleRowIndex + Math.min(0, extraRows.get()));
    }

    private void smoothScrollToEnsureVisible(CellPosition target)
    {
        Point2D currentXY = new Point2D(getCurrentScrollX(), getCurrentScrollY());
        Bounds targetBounds = getPixelPosition(target);
        double deltaX = 0.0;
        double deltaY = 0.0;
        if (targetBounds.getMinX() < currentXY.getX())
        {
            // Off screen to left, scroll til it's visible plus a margin:
            deltaX = targetBounds.getMinX() - currentXY.getX() - 20;
        }
        else if (targetBounds.getMaxX() > currentXY.getX() + container.getWidth())
        {
            // Off screen to right:
            deltaX = targetBounds.getMaxX() - (currentXY.getX() + container.getWidth()) + 20;
        }

        if (targetBounds.getMinY() < currentXY.getY())
        {
            // Off screen above, scroll til it's visible plus a margin:
            deltaY = targetBounds.getMinY() - currentXY.getY() - 20;
        }
        else if (targetBounds.getMaxY() > currentXY.getY() + container.getHeight())
        {
            // Off screen below:
            deltaY = targetBounds.getMaxY() - (currentXY.getY() + container.getHeight()) + 20;
        }

        if (scrollLockedTo != null)
        {
            if (scrollLockedTo.getSecond().includesHorizontal() && scrollLockedTo.getSecond().includesVertical())
            {
                scrollLockedTo.getFirst().smoothScroll(deltaX, deltaY);
                deltaX = 0.0;
                deltaY = 0.0;
            }
            else if (scrollLockedTo.getSecond().includesHorizontal())
            {
                scrollLockedTo.getFirst().smoothScroll(deltaX, 0.0);
                deltaX = 0.0;
            }
            else if (scrollLockedTo.getSecond().includesVertical())
            {
                scrollLockedTo.getFirst().smoothScroll(0.0, deltaY);
                deltaY = 0.0;
            }
        }

        if (deltaX != 0.0 || deltaY != 0.0)
            smoothScroll(deltaX, deltaY);
    }

    private Bounds getPixelPosition(CellPosition target)
    {
        double minX = sumColumnWidths(0, target.columnIndex);
        double minY = rowHeight * target.rowIndex;
        return new BoundingBox(minX, minY, columnWidths[target.columnIndex], rowHeight);
    }

    public static class CellPosition
    {
        // Both are zero-based:
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
