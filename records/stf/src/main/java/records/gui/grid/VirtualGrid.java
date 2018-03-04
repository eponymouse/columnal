package records.gui.grid;

import annotation.help.qual.UnknownIfHelp;
import annotation.qual.UnknownIfValue;
import annotation.recorded.qual.UnknownIfRecorded;
import annotation.units.AbsColIndex;
import annotation.units.AbsRowIndex;
import annotation.userindex.qual.UnknownIfUserIndex;
import com.google.common.collect.Streams;
import javafx.beans.binding.BooleanExpression;
import javafx.beans.binding.DoubleExpression;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
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
import javafx.scene.control.ScrollBar;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;
import log.Log;
import org.checkerframework.checker.initialization.qual.Initialized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.UnknownKeyFor;
import org.checkerframework.checker.units.qual.UnknownUnits;
import org.checkerframework.dataflow.qual.Pure;
import org.fxmisc.wellbehaved.event.EventPattern;
import org.fxmisc.wellbehaved.event.InputMap;
import org.fxmisc.wellbehaved.event.Nodes;
import records.data.CellPosition;
import records.gui.grid.VirtualGridSupplier.ContainerChildren;
import records.gui.grid.VirtualGridSupplier.ViewOrder;
import records.gui.grid.VirtualGridSupplier.VisibleDetails;
import records.gui.stable.ScrollBindable;
import records.gui.stable.ScrollGroup;
import records.gui.stable.ScrollGroup.ScrollLock;
import records.gui.stable.ScrollGroup.Token;
import records.gui.stable.ScrollResult;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FXPlatformBiConsumer;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;
import utility.gui.GUI;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@OnThread(Tag.FXPlatform)
public class VirtualGrid implements ScrollBindable
{
    private final List<VirtualGridSupplier<? extends Node>> nodeSuppliers = new ArrayList<>();
    private final List<GridArea> gridAreas = new ArrayList<>();
    
    private final Container container;
    private static final int MAX_EXTRA_ROW_COLS = 12;
    private final ScrollBar hBar;
    private final ScrollBar vBar;
    private final ScrollGroup scrollGroup;
    private final ArrayList<SelectionListener> selectionListeners = new ArrayList<>();
    // Used as a sort of lock on updating the scroll bars to prevent re-entrant updates:
    private boolean settingScrollBarVal = false;

    // The first index to render.  This is not actually necessarily the same
    // as first really-visible, if we are currently doing some smooth scrolling,
    // which may require rendering some items which are slightly off-screen:
    private @AbsColIndex int firstRenderColumnIndex;
    private @AbsRowIndex int firstRenderRowIndex;
    // Offset of first cell being rendered.  Always zero or negative:
    private double firstRenderColumnOffset;
    private double firstRenderRowOffset;
    private int renderRowCount;
    private int renderColumnCount;
    // Where is our theoretical scroll position?   We don't use this for any rendering,
    // only for knowing where to scroll to on next scroll (because we may need to render
    // extra portions outside the logical scroll position).
    private @AbsColIndex int logicalScrollColumnIndex;
    private @AbsRowIndex int logicalScrollRowIndex;
    // What is the offset of first item?  Always between negative width of current item and zero.  Never positive.
    private double logicalScrollColumnOffset;
    private double logicalScrollRowOffset;
    
    
    private static final @AbsRowIndex int MIN_ROWS = CellPosition.row(10);
    private static final @AbsColIndex int MIN_COLS = CellPosition.col(10);
    
    private final ObjectProperty<@AbsRowIndex Integer> currentKnownRows = new SimpleObjectProperty<>(MIN_ROWS);
    private final ObjectProperty<@AbsColIndex Integer> currentColumns = new SimpleObjectProperty<>(MIN_COLS);

    // Package visible to let sidebars access it
    static final double rowHeight = 24;
    static final double defaultColumnWidth = 100;

    private final Map<Integer, Double> customisedColumnWidths = new HashMap<>();

    // null means the grid doesn't have focus:
    private final ObjectProperty<@Nullable CellSelection> selection = new SimpleObjectProperty<>(null);

    private final BooleanProperty atLeftProperty = new SimpleBooleanProperty(false);
    private final BooleanProperty atRightProperty = new SimpleBooleanProperty(false);
    private final BooleanProperty atTopProperty = new SimpleBooleanProperty(false);
    private final BooleanProperty atBottomProperty = new SimpleBooleanProperty(false);
    
    // A sort of mutex to stop re-entrance to the updateSizeAndPositions() method:
    private boolean updatingSizeAndPositions = false;
    
    private final VirtualGridSupplierFloating supplierFloating = new VirtualGridSupplierFloating();

    @OnThread(Tag.FXPlatform)
    public VirtualGrid(@Nullable FXPlatformBiConsumer<CellPosition, Point2D> createTable)
    {
        if (createTable != null)
            nodeSuppliers.add(new CreateTableButtonSupplier(createTable));
        nodeSuppliers.add(supplierFloating);
        this.hBar = new ScrollBar();
        this.vBar = new ScrollBar();
        this.container = new Container();
        this.container.getStylesheets().add(FXUtility.getStylesheet("virtual-grid.css"));
        scrollGroup = new ScrollGroup(FXUtility.mouse(this)::scrollClampX, FXUtility.mouse(this)::scrollClampY);
/*

                scrollLayoutXBy, MAX_EXTRA_ROW_COLS, targetX -> {
            // Count column widths in that direction until we reach target:
            double curX;
            int startCol;
            if (targetX < 0)
            {
                // If it's negative, we're scrolling left, and we need to show extra
                // rows to the right until they scroll out of view.
                double w = 0;
                for (startCol = firstRenderColumnIndex; startCol < currentColumns.get(); startCol++)
                {
                    w += getColumnWidth(startCol);
                    if (w >= container.getWidth())
                    {
                        break;
                    }
                }
                curX = w + firstRenderColumnOffset - container.getWidth();
                int col;
                for (col = startCol + 1; curX < -targetX; col++)
                {
                    if (col >= currentColumns.get())
                        return currentColumns.get() - startCol;
                    curX += getColumnWidth(col);
                }
                // Will be 0 or positive:
                return col - startCol;
            }
            else
            {
                // Opposite: scrolling right, need extra rows left until scroll out of view:
                startCol = firstRenderColumnIndex;
                int col;
                curX = firstRenderColumnOffset;
                for (col = startCol - 1; curX > -targetX; col--)
                {
                    if (col < 0)
                        return -startCol;
                    curX -= getColumnWidth(col);
                }
                // Will be 0 or negative:
                return col - startCol;
            }
        }
                , FXUtility.mouse(this)::scrollLayoutYBy
                , y -> (int)(Math.signum(-y) * Math.ceil(Math.abs(y) / rowHeight))); */
        scrollGroup.add(FXUtility.mouse(this), ScrollLock.BOTH);
        container.translateXProperty().bind(scrollGroup.translateXProperty());
        container.translateYProperty().bind(scrollGroup.translateYProperty());
        container.addEventFilter(ScrollEvent.ANY, scrollEvent -> {
            scrollGroup.requestScroll(scrollEvent);
            scrollEvent.consume();
        });


        @Initialized @NonNull ObjectProperty<@Nullable CellSelection> selectionFinal = this.selection;
        RectangleOverlayItem selectionRectangleOverlayItem = new RectangleOverlayItem(ViewOrder.OVERLAY_ACTIVE)
        {
            private final BooleanExpression hasSelection = selectionFinal.isNotNull();
            
            @Override
            protected Optional<RectangleBounds> calculateBounds(VisibleDetails<@AbsRowIndex Integer> rowBounds, VisibleDetails<@AbsColIndex Integer> columnBounds)
            {
                CellSelection selection = selectionFinal.get();
                if (selection == null)
                    return Optional.empty();
                else
                    return Optional.of(selection.getSelectionDisplayRectangle());
            }

            @Override
            protected void style(Rectangle r)
            {
                r.getStyleClass().add("virt-grid-selection-overlay");
                r.visibleProperty().bind(hasSelection);
            }
        };
        supplierFloating.addItem(selectionRectangleOverlayItem);
        
        selection.addListener(new ChangeListener<@Nullable CellSelection>()
        {
            @Override
            @OnThread(value = Tag.FXPlatform, ignoreParent = true)
            public void changed(ObservableValue<? extends @Nullable CellSelection> prop, @Nullable CellSelection oldVal, @Nullable CellSelection s)
            {
                if (s != null)
                {
                    FXUtility.mouse(VirtualGrid.this).smoothScrollToEnsureVisible(s.positionToEnsureInView());
                }
                for (Iterator<SelectionListener> iterator = FXUtility.mouse(VirtualGrid.this).selectionListeners.iterator(); iterator.hasNext(); )
                {
                    SelectionListener selectionListener = iterator.next();
                    if (selectionListener.selectionChanged(oldVal, s) == ListenerOutcome.REMOVE)
                        iterator.remove();
                }
                FXUtility.mouse(VirtualGrid.this).container.redoLayout();
            }
        });
    }

    private double scrollClampX(double idealScrollBy)
    {
        idealScrollBy = -idealScrollBy;
        if (idealScrollBy < 0)
        {
            // Scrolling the position to the left:
            
            // We can scroll to the left edge of the current item, for sure.
            // We add the offset because it's negative:
            double maxScroll = getColumnWidth(logicalScrollColumnIndex) + logicalScrollColumnOffset;
            for (int index = logicalScrollColumnIndex - 1; index >= 0; index--)
            {
                maxScroll -= getColumnWidth(index);
                // Short-circuit: if we already showed we can scroll as far as we want to, stop:
                if (maxScroll < idealScrollBy)
                    return -idealScrollBy;
            }
            // Math.max gets us the least-negative number:
            return -Math.max(maxScroll, idealScrollBy);
        }
        else if (idealScrollBy > 0)
        {
            // Scrolling the position to the right:
            // We measure distance to the very right hand edge of all columns, then subtract
            // our own width to work out where the left edge can go:
            
            // The right edge is at least scrolling to right of current first item:
            double distToRightEdge = -logicalScrollColumnOffset;

            @AbsColIndex int curColumns = currentColumns.get();
            double paneWidth = container.getWidth();
            for (int index = logicalScrollColumnIndex + 1; index < curColumns; index++)
            {
                distToRightEdge += getColumnWidth(index);
                // Short-circuit if we already know we can scroll far enough:
                if (distToRightEdge - paneWidth < idealScrollBy)
                    return -idealScrollBy;
            }
            return -Math.max(distToRightEdge - paneWidth, idealScrollBy);
        }
        return -idealScrollBy;
    }

    /**
     * What's the most we can scroll by in the given direction?  If less than param, return
     * the clamped value.
     */
    private double scrollClampY(double idealScrollBy)
    {
        if (idealScrollBy > 0)
        {
            // Furthest we could scroll is all the way to the top:
            double maxScroll = logicalScrollRowIndex * rowHeight + logicalScrollRowOffset;
            return Math.min(maxScroll, idealScrollBy);
        }
        else if (idealScrollBy < 0)
        {
            // The furthest we scroll is until the last row rests at the bottom of the window:
            double lastScrollPos = currentKnownRows.get() * rowHeight - container.getHeight();
            double maxScroll = -(lastScrollPos - (logicalScrollRowIndex * rowHeight + logicalScrollRowOffset));
            // Don't start scrolling backwards, though:
            if (maxScroll > 0)
                maxScroll = 0;
            // We are both negative, so Math.max gets us the least-negative item:
            return Math.max(maxScroll, idealScrollBy);
        }
        // Must be zero; no need to clamp:
        return idealScrollBy;
    }

    private @Nullable CellPosition getCellPositionAt(double x, double y)
    {
        @AbsColIndex int colIndex;
        x -= firstRenderColumnOffset;
        for (colIndex = firstRenderColumnIndex; colIndex < currentColumns.get(); colIndex++)
        {
            x -= getColumnWidth(colIndex);
            if (x < 0.0)
            {
                break;
            }
        }
        if (x > 0.0)
            return null;
        y -= firstRenderRowOffset;
        @SuppressWarnings("units")
        @AbsRowIndex int rowIndex = (int) Math.floor(y / rowHeight) + firstRenderRowIndex;
        if (rowIndex >= getLastSelectableRowGlobal())
            return null;
        return new CellPosition(rowIndex, colIndex);
    }

    @Override
    public void scrollXLayoutBy(Token token, double extraPixelsToShowBefore, double scrollBy, double extraPixelsToShowAfter)
    {
        // We basically do two scrolls.  One to scroll from existing logical position by the given number of pixels,
        // and store that in logical position.  Then a second to scroll from new logical position by
        // the extra number of pixels before, to get the first render.  Finally, we calculate how many
        // items we need to render in the pane overall.
        int curCol = logicalScrollColumnIndex;
        double curOffset = logicalScrollColumnOffset;
        @AbsColIndex int totalColumns = currentColumns.get();
        
        for (int miniScroll = 0; miniScroll < 2; miniScroll++)
        {
            double remainingScroll = miniScroll == 0 ? scrollBy : -extraPixelsToShowBefore;

            // Remember here how the scrolling offsets work.  If you have column 5 with content ABCDE, and you
            // have a curOffset of minus 2, then we render like this:
            //   |
            // AB CDE
            //   |
            // If you move to a curOffset of minus 4 then you get:
            //     |
            // ABCD E
            //     |
            // That is, making curOffset more negative scrolls *right*.  So if your remainingScroll is negative
            // (meaning a request to scroll left), you should actually make curOffset more positive
            
            // Can we just do the scroll within the given column?
            double startColumnWidth = getColumnWidth(curCol);
            if (remainingScroll == 0.0 || (-startColumnWidth < curOffset + remainingScroll && curOffset + remainingScroll <= 0))
            {
                curOffset += remainingScroll;
            }
            else
            {
                // The first thing we do is scroll in the right direction so that we are at the left edge of a column,
                // because that makes the loop afterwards a hell of a lot simpler:
                int dir;
                if (remainingScroll > 0)
                {
                    remainingScroll += curOffset;
                    dir = -1;
                }
                else
                {
                    remainingScroll += startColumnWidth + curOffset;
                    curCol += 1;
                    dir = 1;                    
                }
                
                // Now we head through the columns until we've used up all our scroll.
                // We'll know we're finished when the remainingScroll is between -curColumnWidth, and zero
                while (curCol >= 0 && curCol < totalColumns)
                {
                    double curColumnWidth = getColumnWidth(curCol);
                    if (-curColumnWidth <= remainingScroll && remainingScroll <= 0)
                    {
                        break;
                    }
                    remainingScroll += dir * curColumnWidth;
                    curCol += dir;
                }
                curOffset = remainingScroll;
            }
            
            if (miniScroll == 0)
            {
                logicalScrollColumnIndex = CellPosition.col(curCol);
                logicalScrollColumnOffset = curOffset;
            }
            else
            {
                firstRenderColumnOffset = curOffset;
                firstRenderColumnIndex = CellPosition.col(curCol);
            }
        }
        
        // Now we work out how many columns we need to render:
        @AbsColIndex int lastRenderIndex = firstRenderColumnIndex;
        
        double remainingPixels = extraPixelsToShowBefore + container.getWidth() + extraPixelsToShowAfter;
        
        while (remainingPixels > 0.0 && lastRenderIndex < totalColumns)
        {
            remainingPixels -= getColumnWidth(lastRenderIndex);
            lastRenderIndex += CellPosition.col(1);
        }
        
        renderColumnCount = lastRenderIndex - firstRenderColumnIndex + 1;
        
        updateHBar();

        boolean atLeft = firstRenderColumnIndex == 0 && firstRenderColumnOffset >= -5;
        //FXUtility.setPseudoclass(glass, "left-shadow", !atLeft);

        container.redoLayout();
    }
    
    @Override
    public void scrollYLayoutBy(Token token, double extraPixelsToShowBefore, double scrollBy, double extraPixelsToShowAfter)
    {
        // First scroll to the right logical position:
        class ScrollResult
        {
            private final @AbsRowIndex int row;
            private final double offset;
            
            // Slight abuse of a constructor, but it's only local.  Calculate new scroll result:
            ScrollResult(@AbsRowIndex int existingIndex, double existingOffset, double scrollBy)
            {
                if (scrollBy <= 0.0)
                {
                    int newRows = (int)Math.floor(Math.max(existingOffset -scrollBy, 0.0) / rowHeight);
                    row = existingIndex + CellPosition.row(newRows);
                    offset = newRows * rowHeight - (existingOffset - scrollBy);
                }
                else
                {
                    int newRows = (int)Math.floor(Math.max(Math.abs(scrollBy - (rowHeight + existingOffset)), 0.0) / rowHeight);
                    row = existingIndex - CellPosition.row(newRows);
                    offset = existingOffset - scrollBy + newRows * rowHeight;
                }
            }
        }
        ScrollResult logicalPos = new ScrollResult(logicalScrollRowIndex, logicalScrollRowOffset, scrollBy);
        logicalScrollRowIndex = logicalPos.row;
        logicalScrollRowOffset = logicalPos.offset;
        ScrollResult renderPos = new ScrollResult(logicalScrollRowIndex, logicalScrollRowOffset, -extraPixelsToShowBefore);
        firstRenderRowIndex = renderPos.row;
        firstRenderRowOffset = renderPos.offset;
        ScrollResult bottomVisible = new ScrollResult(firstRenderRowIndex, firstRenderRowOffset, extraPixelsToShowBefore + container.getHeight() + extraPixelsToShowAfter);
        renderRowCount = bottomVisible.row - firstRenderRowIndex + 1;
        
        updateVBar();
        
        container.redoLayout();
    }

    @Override
    public @OnThread(Tag.FXPlatform) void updateClip()
    {
        if (container.clip != null)
        {
            container.clip.setX(-container.getTranslateX());
            container.clip.setY(-container.getTranslateY());
            container.clip.setWidth(container.getWidth());
            container.clip.setHeight(container.getHeight());
            //scrollGroup.updateClip();
        }
    }

    /**
     * Adds the column widths for any column index C where startColIndexIncl <= C < endColIndexExcl
     * If startColIndexIncl >= endColIndexExcl, zero will be returned.
     */
    private double sumColumnWidths(@AbsColIndex int startColIndexIncl, @AbsColIndex int endColIndexExcl)
    {
        double total = 0;
        for (int i = startColIndexIncl; i < endColIndexExcl; i++)
        {
            total += getColumnWidth(i);
        }
        return total;
    }

    private double getColumnWidth(@UnknownInitialization(Object.class) VirtualGrid this, int columnIndex)
    {
        return customisedColumnWidths.getOrDefault(columnIndex, defaultColumnWidth);
    }

    private @AbsRowIndex int getLastSelectableRowGlobal()
    {
        return CellPosition.row(currentKnownRows.get() - 1);
    }

    private double getMaxScrollX()
    {
        return Math.max(0, sumColumnWidths(CellPosition.col(0), currentColumns.get())  - container.getWidth());
    }

    private double getMaxScrollY()
    {
        return Math.max(0, (getLastSelectableRowGlobal() - 1) * rowHeight - container.getHeight());
    }

    private void updateVBar()
    {
        settingScrollBarVal = true;
        double maxScrollY = getMaxScrollY();
        double currentScrollY = getCurrentScrollY(null);
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
        double currentScrollX = getCurrentScrollX(null);
        hBar.setValue(maxScrollX < 1.0 ? 0.0 : (currentScrollX / maxScrollX));
        hBar.setVisibleAmount(maxScrollX < 1.0 ? 1.0 : (container.getWidth() / (maxScrollX + container.getWidth())));
        hBar.setMax(maxScrollX < 1.0 ? 0.0 : 1.0);
        atLeftProperty.set(currentScrollX < 1.0);
        atRightProperty.set(currentScrollX >= maxScrollX - 1.0);
        settingScrollBarVal = false;
    }

    // If param is non-null, overrides our own data
    private double getCurrentScrollY(@Nullable Pair<@AbsRowIndex Integer, Double> pos)
    {
        return (pos == null ? logicalScrollRowIndex : pos.getFirst()) * rowHeight - (pos == null ? logicalScrollRowOffset : pos.getSecond());
    }

    // If param is non-null, overrides our own data
    private double getCurrentScrollX(@Nullable Pair<@AbsColIndex Integer, Double> pos)
    {
        return sumColumnWidths(CellPosition.col(0), pos == null ? logicalScrollColumnIndex : pos.getFirst()) - (pos == null ? logicalScrollColumnOffset : pos.getSecond());
    }

    public void select(@Nullable CellSelection cellSelection)
    {
        // TODO make sure cell is visible
        
        /*
        visibleCells.forEach((visPos, visCell) -> {
            SelectionStatus status = cellSelection == null ? SelectionStatus.UNSELECTED : cellSelection.selectionStatus(visPos);
            FXUtility.setPseudoclass(visCell, "primary-selected-cell", status == SelectionStatus.PRIMARY_SELECTION);
            FXUtility.setPseudoclass(visCell, "secondary-selected-cell", status == SelectionStatus.SECONDARY_SELECTION);
        });
        */
        
        selection.set(cellSelection);
        if (cellSelection != null)
            container.requestFocus();
    }
    
    // Selects cell so that you can navigate around with keyboard
    public void findAndSelect(@Nullable Either<CellPosition, CellSelection> target)
    {
        select(target == null ? null : target.<@Nullable CellSelection>either(
            p -> {
                // See if the position is in a grid area:
                for (GridArea gridArea : gridAreas)
                {
                    if (gridArea.contains(p))
                    {
                        return gridArea.getSelectionForSingleCell(p);
                    }
                }
                return new EmptyCellSelection(p);
            },
            s -> s
        ));
    }

    public Region getNode()
    {
        return container;
    }

    public void positionOrAreaChanged()
    {
        // This calls container.redoLayout():
        updateSizeAndPositions();
    }

    public int _test_getFirstLogicalVisibleRowIncl()
    {
        // This is first row with middle visible, so check for that:
        if (firstRenderRowOffset < -rowHeight / 2.0)
            return firstRenderRowIndex + 1;
        else
            return firstRenderRowIndex;
    }

    public int _test_getLastLogicalVisibleRowExcl()
    {
        int numVisible = (int)Math.round((double)container.getHeight() / rowHeight);
        return firstRenderRowIndex + numVisible;
    }


    public void addNodeSupplier(VirtualGridSupplier<?> cellSupplier)
    {
        nodeSuppliers.add(cellSupplier);
        container.redoLayout();
    }

    public ScrollGroup getScrollGroup()
    {
        return scrollGroup;
    }

    public void onNextSelectionChange(FXPlatformConsumer<@Nullable CellSelection> onChange)
    {
        selectionListeners.add((old, s) -> {
            onChange.consume(s);
            return ListenerOutcome.REMOVE;
        });
    }

    public void move(GridArea gridArea, CellPosition destinationPosition)
    {
        // TODO rearrange
    }

    public Bounds _test_getRectangleBoundsScreen(RectangleBounds rectangleBounds)
    {
        double adjustX = getCurrentScrollX(null);
        double adjustY = getCurrentScrollY(null);
        double x = sumColumnWidths(CellPosition.col(0), rectangleBounds.topLeftIncl.columnIndex);
        double y = rectangleBounds.topLeftIncl.rowIndex * rowHeight;
        return container.localToScreen(new BoundingBox(
            x + adjustX, 
            y + adjustY, 
            sumColumnWidths(rectangleBounds.topLeftIncl.columnIndex, rectangleBounds.bottomRightIncl.columnIndex + CellPosition.col(1)),
            rowHeight * (rectangleBounds.bottomRightIncl.rowIndex + 1 - rectangleBounds.topLeftIncl.rowIndex)
        ));
    }

    public final @Pure VirtualGridSupplierFloating getFloatingSupplier()
    {
        return supplierFloating;
    }

    public final void addSelectionListener(SelectionListener selectionListener)
    {
        selectionListeners.add(selectionListener);
    }

    public double _test_getScrollXPos()
    {
        return getCurrentScrollX(null);
    }

    public double _test_getScrollYPos()
    {
        return getCurrentScrollY(null);
    }

    @OnThread(Tag.FXPlatform)
    private class Container extends Region implements ContainerChildren
    {
        private final Rectangle clip;
        private List<ViewOrder> viewOrders = new ArrayList<>();

        public Container()
        {
            getStyleClass().add("virt-grid");
            // Need this for when JavaFX looks for a default focus target:
            setFocusTraversable(true);

            clip = new Rectangle();
            setClip(clip);

            FXUtility.addChangeListenerPlatformNN(widthProperty(), w -> {updateHBar(); redoLayout();});
            FXUtility.addChangeListenerPlatformNN(heightProperty(), h -> {updateVBar(); redoLayout();});

            /* 
             * The logic for mouse events is as follows.
             * 
             * - If the cell is being edited already, we let add mouse events pass through unfiltered
             *   to the underlying component.
             * - Else if the cell is currently a single cell selection, a single click
             *   will be passed to the cell with an instruction to start editing at that location
             *   (e.g. by setting the caret location).
             * - Else if the cell is not currently a single cell selection, how many clicks?
             *   - Double click: start editing at given point
             *   - Single click: become a single cell selection
             * 
             * To do the first two steps we talk to the node suppliers as they know which cell is at whic
             * location.  For the third one we find the relevant grid area.
             */
            
            EventHandler<? super @UnknownIfRecorded @UnknownKeyFor @UnknownIfValue @UnknownIfUserIndex @UnknownIfHelp @UnknownUnits MouseEvent> clickHandler = mouseEvent -> {

                @Nullable CellPosition cellPosition = getCellPositionAt(mouseEvent.getX(), mouseEvent.getY());
                
                if (cellPosition != null)
                {
                    @NonNull CellPosition cellPositionFinal = cellPosition;
                    boolean editing = nodeSuppliers.stream().anyMatch(g -> g.isEditing(cellPositionFinal));
                    if (editing)
                        return; // Don't capture the events
                    
                    // Not editing, is the cell currently part of a single cell selection:
                    @Nullable CellSelection curSel = VirtualGrid.this.selection.get();
                    boolean selectedByItself = curSel != null && curSel.isExactly(cellPosition);
                    
                    if (selectedByItself || mouseEvent.getClickCount() == 2)
                    {
                        for (VirtualGridSupplier<? extends Node> nodeSupplier : nodeSuppliers)
                        {
                            nodeSupplier.startEditing(new Point2D(mouseEvent.getScreenX(), mouseEvent.getScreenY()), cellPositionFinal);
                        }
                    }
                    else
                    {
                        boolean foundInGrid = false;
                        // Become a single cell selection:
                        for (GridArea gridArea : gridAreas)
                        {
                            @Nullable CellSelection singleCellSelection = gridArea.getSelectionForSingleCell(cellPosition);
                            if (singleCellSelection != null)
                            {
                                select(singleCellSelection);
                                foundInGrid = true;
                                break;
                            }
                        }

                        if (!foundInGrid)
                        {
                            // Belongs to no-one; we must handle it:
                            select(new EmptyCellSelection(cellPosition));
                            FXUtility.mouse(this).requestFocus();
                        }
                    }
                    
                    mouseEvent.consume();
                    redoLayout();
                }
            };
            addEventFilter(MouseEvent.MOUSE_CLICKED, clickHandler);

            EventHandler<? super @UnknownIfRecorded @UnknownKeyFor @UnknownIfValue @UnknownIfUserIndex @UnknownIfHelp @UnknownUnits MouseEvent> capture = mouseEvent -> {
                @Nullable CellPosition cellPosition = getCellPositionAt(mouseEvent.getX(), mouseEvent.getY());

                if (cellPosition != null)
                {
                    // We want to capture the events to prevent clicks reaching the underlying cell,
                    // if the cell is not currently editing
                    @NonNull CellPosition cellPositionFinal = cellPosition;
                    boolean editing = nodeSuppliers.stream().anyMatch(g -> g.isEditing(cellPositionFinal)); 
                    if (!editing)
                        mouseEvent.consume();
                }
            };
            addEventFilter(MouseEvent.MOUSE_PRESSED, capture);
            addEventFilter(MouseEvent.MOUSE_RELEASED, capture);
            
            FXUtility.addChangeListenerPlatformNN(focusedProperty(), focused -> {
                if (!focused)
                {
                    select(null);
                    redoLayout();
                }
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
                            activateCell(focusedCellPosition);
                        }
                        e.consume();
                    }),
                    InputMap.<Event, KeyEvent>consume(EventPattern.keyPressed(KeyCode.SPACE), e -> {
                        @Nullable CellSelection focusedCellPosition = selection.get();
                        if (focusedCellPosition != null)
                        {
                            activateCell(focusedCellPosition);
                        }
                        e.consume();
                    })
            ));
        }

        @Override
        @OnThread(Tag.FX)
        protected void layoutChildren()
        {
            // Note: it is important to not call super.layoutChildren() here, because Parent will
            // resize its children to their preferred size, which we do not want.
            
            // We do nothing directly here because redoLayout will have done it.  We don't call redoLayout during
            // the actual layout pass, because redoLayout adds and removes children, and JavaFX goes a bit loopy
            // if you do this during its layout pass.
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

        private void move(boolean extendSelection, int rows, int columns)
        {
            @Nullable CellSelection focusedCellPos = selection.get();
            if (focusedCellPos != null)
            {
                findAndSelect(focusedCellPos.move(extendSelection, rows, columns));
            }
        }

        private void home(boolean extendSelection)
        {
            @Nullable CellSelection focusedCellPos = selection.get();
            if (focusedCellPos != null)
            {
                select(focusedCellPos.atHome(extendSelection));
            }
        }

        private void end(boolean extendSelection)
        {
            @Nullable CellSelection focusedCellPos = selection.get();
            if (focusedCellPos != null)
            {
                select(focusedCellPos.atEnd(extendSelection));
            }
        }

        private void redoLayout(@UnknownInitialization(Region.class) Container this)
        {
            double x = firstRenderColumnOffset;
            double y = firstRenderRowOffset;

            // We may not need the +1, but play safe:
            int newNumVisibleRows = Math.min(currentKnownRows.get() - firstRenderRowIndex, (int)Math.ceil(getHeight() / rowHeight) + 1);
            int newNumVisibleCols = 0;
            for (int column = firstRenderColumnIndex; x < getWidth() && column < currentColumns.get(); column++)
            {
                newNumVisibleCols += 1;
                x += getColumnWidth(column);
            }
            VirtualGrid.this.renderRowCount = newNumVisibleRows;
            VirtualGrid.this.renderColumnCount = newNumVisibleCols;

            // This includes extra rows needed for smooth scrolling:
            @AbsRowIndex int firstDisplayRow = firstRenderRowIndex;
            @AbsRowIndex int lastDisplayRowExcl = firstRenderRowIndex + CellPosition.row(renderRowCount);
            @AbsColIndex int firstDisplayCol = firstRenderColumnIndex;
            @AbsColIndex int lastDisplayColExcl = firstRenderColumnIndex + CellPosition.col(renderColumnCount);
            
            //Log.debug("Rows: " + firstDisplayRow + " to " + (lastDisplayRowExcl - 1) + " incl extra: " + extraRowsForSmoothScroll.get() + " ideal first: " + firstRenderRowIndex);

            VisibleDetails<@AbsColIndex Integer> columnBounds = new VisibleDetails<@AbsColIndex Integer>(firstDisplayCol, lastDisplayColExcl - CellPosition.col(1))
            {
                @Override
                public double getItemCoord(@AbsColIndex Integer itemIndex)
                {
                    return firstRenderColumnOffset + sumColumnWidths(firstDisplayCol, itemIndex);
                }

                @Override
                @OnThread(Tag.FXPlatform)
                public Optional<@AbsColIndex Integer> getItemIndexForScreenPos(Point2D screenPos)
                {
                    Point2D localCoord = screenToLocal(screenPos);
                    double x = firstRenderColumnOffset;
                    for (@AbsColIndex int i = firstRenderColumnIndex; i < lastDisplayColExcl; i++)
                    {
                        double nextX = x + getColumnWidth(i);
                        if (x <= localCoord.getX() && localCoord.getX() < nextX)
                            return Optional.of(i);
                        x = nextX;
                    }
                    return Optional.empty();
                }
            };
            VisibleDetails<@AbsRowIndex Integer> rowBounds = new VisibleDetails<@AbsRowIndex Integer>(firstDisplayRow, lastDisplayRowExcl - CellPosition.row(1))
            {
                @Override
                public double getItemCoord(@AbsRowIndex Integer itemIndex)
                {
                    return firstRenderRowOffset + rowHeight * (itemIndex - firstDisplayRow);
                }

                @Override
                public Optional<@AbsRowIndex Integer> getItemIndexForScreenPos(Point2D screenPos)
                {
                    Point2D localCoord = screenToLocal(screenPos);
                    @AbsRowIndex int theoretical = CellPosition.row((int)Math.floor((localCoord.getY() - firstRenderRowOffset) / rowHeight)) + firstDisplayRow;
                    if (firstRenderRowIndex <= theoretical && theoretical < lastDisplayRowExcl)
                        return Optional.of(theoretical);
                    else
                        return Optional.empty();
                }
            };

            for (VirtualGridSupplier<? extends Node> nodeSupplier : nodeSuppliers)
            {
                nodeSupplier.layoutItems(FXUtility.mouse(this), rowBounds, columnBounds);
            }
            //Log.debug("Children: " + getChildren().size());
            
            updateClip();
            requestLayout();
        }

        @Override
        public Pair<DoubleExpression, DoubleExpression> add(Node node, ViewOrder viewOrder)
        {
            // Need to insert at right place:
            // Children are kept sorted by view order:
            int insertionIndex = 0;
            while (insertionIndex < viewOrders.size() && viewOrders.get(insertionIndex).ordinal() < viewOrder.ordinal())
                insertionIndex += 1;
            getChildren().add(insertionIndex, node);
            viewOrders.add(insertionIndex, viewOrder);
            return new Pair<>(translateXProperty(), translateYProperty());
        }

        @Override
        public void remove(Node node)
        {
            int index = getChildren().indexOf(node);
            getChildren().remove(index);
            viewOrders.remove(index);
        }
    }

    private void updateSizeAndPositions()
    {
        if (updatingSizeAndPositions)
            return;
        updatingSizeAndPositions = true;
        
        // Three things to do:
        //   - Get each grid area to update its known size (which may involve calling us back)
        //   - Check for overlaps between tables, and reshuffle if needed
        //   - Update our known overall grid size

        @SuppressWarnings("units")
        @AbsRowIndex int maxExtra = MAX_EXTRA_ROW_COLS;
        List<@AbsRowIndex Integer> rowSizes = Utility.<GridArea, @AbsRowIndex Integer>mapList(gridAreas, gridArea -> gridArea.getAndUpdateBottomRow(currentKnownRows.get() + maxExtra, this::updateSizeAndPositions));
                
        // The plan to fix overlaps: we go from the left-most column across to
        // the right-most, keeping track of which tables exist in this column.
        // If for any given column, we find an overlap, we pick one table
        // to remain in this column, then punt any overlappers to the right
        // until they no longer overlap.  Then we continue this process,
        // making sure to take account that tables may have moved, and thus the
        // furthest column may also have changed.
        
        // Pairs each grid area with its integer priority.  Starts in order of table left-most index:
        ArrayList<Pair<Long, GridArea>> gridAreas = new ArrayList<>(
                Streams.mapWithIndex(this.gridAreas.stream().sorted(Comparator.comparing(t -> t.getPosition().columnIndex)),
                        (x, i) -> new Pair<> (i, x))
            .collect(Collectors.toList()));

        // Any currently open grid areas.  These will not overlap with each other vertically:
        ArrayList<Pair<Long, GridArea>> openGridAreas = new ArrayList<>();
        
        // Note: gridAreas.size() may change during the loop!  But nothing at position
        // before i will be modified or looked at.
        gridAreaLoop: for (int i = 0; i < gridAreas.size(); i++)
        {
            Pair<Long, GridArea> cur = gridAreas.get(i);
            
            // Check for overlap with open grid areas:
            for (Pair<Long, GridArea> openGridArea : openGridAreas)
            {
                if (overlap(openGridArea.getSecond(), cur.getSecond()))
                {
                    // Shunt us sideways so that we don't overlap, and add to right point in list
                    // We may overlap more tables, but that is fine, we will get shunted again
                    // next time round if needed
                    CellPosition curPos = cur.getSecond().getPosition();
                    curPos = new CellPosition(curPos.rowIndex, openGridArea.getSecond().getBottomRightIncl().columnIndex);
                    cur.getSecond().setPosition(curPos);
                    
                    // Now need to add us to the gridAreas list at correct place.  We don't
                    // worry about removing ourselves as it's more hassle than it's worth.
                    
                    // The right place is the end, or the first time that we compare less
                    // when comparing left-hand edge coordinates (and secondarily, priority)
                    for (int j = i + 1; j < gridAreas.size(); j++)
                    {
                        int lhs = gridAreas.get(j).getSecond().getPosition().columnIndex;
                        if (curPos.columnIndex < lhs
                                 || (curPos.columnIndex == lhs && cur.getFirst() < gridAreas.get(j).getFirst()))
                        {
                            gridAreas.add(j, cur);
                            continue gridAreaLoop;
                        }
                    }
                    
                    gridAreas.add(cur);
                    continue gridAreaLoop;
                }
            }
            // Close any grid areas that we have gone to the right of:
            openGridAreas.removeIf(p -> p.getSecond().getBottomRightIncl().columnIndex <= cur.getSecond().getPosition().columnIndex);
            
            // Add ourselves to the open areas:
            openGridAreas.add(cur);
        }
        
        currentColumns.set(
            Utility.maxCol(MIN_COLS, 
                gridAreas.stream()
                        .<@AbsColIndex Integer>map(g -> Utility.boxCol(g.getSecond().getBottomRightIncl().columnIndex))
                        .max(Comparator.comparingInt(x -> x))
                    .<@AbsColIndex Integer>orElse(CellPosition.col(0))
                    + CellPosition.col(2)));
        currentKnownRows.set(Utility.maxRow(MIN_ROWS, rowSizes.stream().max(Comparator.comparingInt(x -> x)).<@AbsRowIndex Integer>orElse(CellPosition.row(0)) + CellPosition.row(2)));
        container.redoLayout();
        updatingSizeAndPositions = false;
    }

    private boolean overlap(GridArea a, GridArea b)
    {
        int aLeftIncl = a.getPosition().columnIndex;
        int aRightIncl = a.getBottomRightIncl().columnIndex;
        int aTopIncl = a.getPosition().rowIndex;
        int aBottomIncl = a.getBottomRightIncl().rowIndex;
        
        int bLeftIncl = b.getPosition().columnIndex;
        int bRightIncl = b.getBottomRightIncl().columnIndex;
        int bTopIncl = b.getPosition().rowIndex;
        int bBottomIncl = b.getBottomRightIncl().rowIndex;
        boolean distinctHoriz = aLeftIncl > bRightIncl || bLeftIncl > aRightIncl;
        boolean distinctVert = aTopIncl > bBottomIncl || bTopIncl > aBottomIncl;
        boolean overlap = !(distinctHoriz || distinctVert);
        /*
        if (overlap)
        {
            Log.logStackTrace("Found overlap between " + a + " " + a.getPosition() + "-(" + aRightIncl + ", " + aBottomIncl + ")"
                + " and " + b + " " + b.getPosition() + "-(" + bRightIncl + ", " + bBottomIncl + ")");
        }
        */
        return overlap;
    }

    private void activateCell(@Nullable CellSelection cellPosition)
    {
        // TODO
    }

    private Bounds getPixelPosition(CellPosition target)
    {
        double minX = sumColumnWidths(CellPosition.col(0), target.columnIndex);
        double minY = rowHeight * target.rowIndex;
        return new BoundingBox(minX, minY, getColumnWidth(target.columnIndex), rowHeight);
    }

    private void smoothScrollToEnsureVisible(CellPosition target)
    {
        Point2D currentXY = new Point2D(getCurrentScrollX(null), getCurrentScrollY(null));
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

        if (deltaX != 0.0 || deltaY != 0.0)
            scrollGroup.requestScrollBy(deltaX, deltaY);
    }
    
    public void addGridAreas(Collection<GridArea> gridAreas)
    {
        this.gridAreas.addAll(gridAreas);
        for (GridArea gridArea : gridAreas)
        {
            gridArea.addedToGrid(this);
        }
        updateSizeAndPositions();
    }

    public void removeGridArea(GridArea gridArea)
    {
        gridAreas.remove(gridArea);
        updateSizeAndPositions();
    }

    // A really simple class that manages a single button which is shown when an empty location is focused
    private class CreateTableButtonSupplier extends VirtualGridSupplier<Button>
    {
        private @MonotonicNonNull Button button;
        // Button position, last mouse position on screen:
        private final FXPlatformBiConsumer<CellPosition, Point2D> createTable;

        private CreateTableButtonSupplier(FXPlatformBiConsumer<CellPosition, Point2D> createTable)
        {
            this.createTable = createTable;
        }

        @Override
        void layoutItems(ContainerChildren containerChildren, VisibleDetails<@AbsRowIndex Integer> rowBounds, VisibleDetails<@AbsColIndex Integer> columnBounds)
        {
            if (button == null)
            {
                Point2D[] lastMousePos = new Point2D[1];
                button = GUI.button("create.table", () -> {
                    @Nullable CellSelection curSel = selection.get();
                    if (curSel instanceof EmptyCellSelection)
                    {
                        // Offer to create a table at that location, but we need to ask data or transform, if it's not the first table:
                        createTable.consume(((EmptyCellSelection)curSel).position, lastMousePos[0]);
                    }
                }, "create-table-grid-button");
                button.setFocusTraversable(false);
                button.addEventFilter(MouseEvent.ANY, e -> {
                    lastMousePos[0] = new Point2D(e.getScreenX(), e.getScreenY());
                });
                containerChildren.add(button, ViewOrder.STANDARD);
            }

            @Nullable CellSelection curSel = selection.get();
            if (curSel != null && curSel instanceof EmptyCellSelection)
            {
                button.setVisible(true);
                CellPosition pos = ((EmptyCellSelection) curSel).position;
                double x = columnBounds.getItemCoord(pos.columnIndex);
                double y = rowBounds.getItemCoord(pos.rowIndex);
                button.resizeRelocate(
                    x,
                    y,
                    Math.max(button.minWidth(100.0), columnBounds.getItemCoordAfter(pos.columnIndex) - x),
                    rowBounds.getItemCoordAfter(pos.rowIndex) - y
                );
            }
            else
            {
                button.setVisible(false);
            }
        }
    }
    
    private class EmptyCellSelection implements CellSelection
    {
        private final CellPosition position;

        public EmptyCellSelection(CellPosition position)
        {
            this.position = position;
        }

        @Override
        public CellSelection atHome(boolean extendSelection)
        {
            // Should we make home do anything if on empty spot?
            return this;
        }

        @Override
        public CellSelection atEnd(boolean extendSelection)
        {
            // Should we make end do anything if on empty spot?
            return this;
        }
        
        @Override
        public Either<CellPosition, CellSelection> move(boolean extendSelection, int _byRows, int _byColumns)
        {
            @AbsRowIndex int byRows = CellPosition.row(_byRows);
            @AbsColIndex int byColumns = CellPosition.col(_byColumns);
            CellPosition newPos = new CellPosition(
                Utility.minRow(currentKnownRows.get() - CellPosition.row(1), Utility.maxRow(position.rowIndex + byRows, CellPosition.row(0))), 
                Utility.minCol(currentColumns.get() - CellPosition.col(1), Utility.maxCol(position.columnIndex + byColumns, CellPosition.col(0)))
            );
            if (newPos.equals(position))
                return Either.right(this); // Not moving
            // Go through each grid area and see if it contains the position:
            for (GridArea gridArea : gridAreas)
            {
                if (gridArea.contains(newPos))
                {
                    return Either.left(newPos);
                }
            }
            return Either.right(new EmptyCellSelection(newPos));
        }

        @Override
        public CellPosition positionToEnsureInView()
        {
            return position;
        }

        @Override
        public RectangleBounds getSelectionDisplayRectangle()
        {
            return new RectangleBounds(position, position);
        }

        @Override
        public boolean isExactly(CellPosition cellPosition)
        {
            return position.equals(cellPosition);
        }

        @Override
        public boolean includes(GridArea tableDisplay)
        {
            // Empty cell overlaps no grid area:
            return false;
        }
    }
    
    public static enum ListenerOutcome { KEEP, REMOVE }
    
    public static interface SelectionListener
    {
        public ListenerOutcome selectionChanged(@Nullable CellSelection oldSelection, @Nullable CellSelection newSelection);
    }
}
