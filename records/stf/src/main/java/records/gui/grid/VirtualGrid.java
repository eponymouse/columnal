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
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
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
import javafx.scene.control.ScrollBar;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;
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
    // Used as a sort of lock on updating the scroll bars to prevent re-entrant updates:
    private boolean settingScrollBarVal = false;

    // The first index logically visible.  This is not actually necessarily the same
    // as first really-visible, if we are currently doing some smooth scrolling:
    private @AbsColIndex int firstVisibleColumnIndex;
    private @AbsRowIndex int firstVisibleRowIndex;
    // Offset of top visible cell.  Always -rowHeight <= y <= 0
    private double firstVisibleColumnOffset;
    private double firstVisibleRowOffset;
    private int visibleRowCount;
    private int visibleColumnCount;
    // How many extra rows to show off-screen, to account for scrolling (when actual display can lag logical display).
    // Negative means we need them left/above, positive means we need them below/right:
    private final IntegerProperty extraRowsForSmoothScroll = new SimpleIntegerProperty(0);
    private final IntegerProperty extraColsForSmoothScroll = new SimpleIntegerProperty(0);

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
    
    // How many extra rows to show off-screen, to account for scrolling (when actual display can lag logical display).
    // Negative means we need them left/above, positive means we need them below/right:
    private final IntegerProperty extraRowsForScrolling = new SimpleIntegerProperty(0);
    private final IntegerProperty extraColsForScrolling = new SimpleIntegerProperty(0);
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
        scrollGroup = new ScrollGroup(
                FXUtility.mouse(this)::scrollLayoutXBy, MAX_EXTRA_ROW_COLS, targetX -> {
            // Count column widths in that direction until we reach target:
            double curX;
            int startCol;
            if (targetX < 0)
            {
                // If it's negative, we're scrolling left, and we need to show extra
                // rows to the right until they scroll out of view.
                double w = 0;
                for (startCol = firstVisibleColumnIndex; startCol < currentColumns.get(); startCol++)
                {
                    w += getColumnWidth(startCol);
                    if (w >= container.getWidth())
                    {
                        break;
                    }
                }
                curX = w + firstVisibleColumnOffset - container.getWidth();
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
                startCol = firstVisibleColumnIndex;
                int col;
                curX = firstVisibleColumnOffset;
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
                , y -> (int)(Math.signum(-y) * Math.ceil(Math.abs(y) / rowHeight)));
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
        
        FXUtility.addChangeListenerPlatform(selection, s -> {
            if (s != null)
            {
                FXUtility.mouse(this).smoothScrollToEnsureVisible(s.positionToEnsureInView());
            }
            container.redoLayout();
        });
    }

    private @Nullable CellPosition getCellPositionAt(double x, double y)
    {
        @AbsColIndex int colIndex;
        x -= firstVisibleColumnOffset;
        for (colIndex = firstVisibleColumnIndex; colIndex < currentColumns.get(); colIndex++)
        {
            x -= getColumnWidth(colIndex);
            if (x < 0.0)
            {
                break;
            }
        }
        if (x > 0.0)
            return null;
        y -= firstVisibleRowOffset;
        @SuppressWarnings("units")
        @AbsRowIndex int rowIndex = (int) Math.floor(y / rowHeight) + firstVisibleRowIndex;
        if (rowIndex >= getLastSelectableRowGlobal())
            return null;
        return new CellPosition(rowIndex, colIndex);
    }

    // This is the canonical scroll method which all scroll
    // attempts should pass through, to avoid duplicating the
    // update code
    @Override
    public void showAtOffset(@Nullable Pair<@AbsRowIndex Integer, Double> rowAndPixelOffset, @Nullable Pair<@AbsColIndex Integer, Double> colAndPixelOffset)
    {
        if (rowAndPixelOffset != null)
        {
            @AbsRowIndex int row = rowAndPixelOffset.getFirst();
            double rowPixelOffset = rowAndPixelOffset.getSecond();
            if (row < 0)
            {
                row = CellPosition.row(0);
                // Can't scroll above top of first item:
                rowPixelOffset = 0.0;
            }
            else if (row > Math.max(0, currentKnownRows.get() - 1))
            {
                // Can't scroll beyond showing the last cell at the top of the window:
                row = Utility.maxRow(CellPosition.row(0), currentKnownRows.get() - CellPosition.row(1));
                rowPixelOffset = 0;
            }
            this.firstVisibleRowOffset = rowPixelOffset;
            this.firstVisibleRowIndex = row;
            updateVBar();

            boolean atTop = firstVisibleRowIndex == 0 && firstVisibleRowOffset >= -5;
            //FXUtility.setPseudoclass(glass, "top-shadow", !atTop);
        }
        if (colAndPixelOffset != null)
        {
            @AbsColIndex int col = Utility.maxCol(colAndPixelOffset.getFirst(), CellPosition.col(0));
            this.firstVisibleColumnOffset = colAndPixelOffset.getSecond();
            this.firstVisibleColumnIndex = col;
            updateHBar();

            boolean atLeft = firstVisibleColumnIndex == 0 && firstVisibleColumnOffset >= -5;
            //FXUtility.setPseudoclass(glass, "left-shadow", !atLeft);
        }
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

    // This scrolls just the layout, without smooth scrolling
    private ScrollResult<@AbsColIndex Integer> scrollLayoutXBy(double x, ScrollGroup.Token token)
    {
        double prevScroll = getCurrentScrollX(null);
        Pair<@AbsColIndex Integer, Double> pos = scrollXToPixel(prevScroll + x, token);
        return new ScrollResult<>(getCurrentScrollX(pos) - prevScroll, pos);
    }

    // This scrolls just the layout, without smooth scrolling
    // Returns the amount that we actually scrolled by, which will either
    // be given parameter, or otherwise it will have been clamped because we tried
    // to scroll at the very top or very bottom
    private ScrollResult<@AbsRowIndex Integer> scrollLayoutYBy(double y, ScrollGroup.Token token)
    {
        double prevScroll = getCurrentScrollY(null);
        Pair<@AbsRowIndex Integer, Double> pos = scrollYToPixel(prevScroll + y, token);
        return new ScrollResult<>(getCurrentScrollY(pos) - prevScroll, pos);
    }

    // This method should only be called by ScrollGroup, hence the magic token:
    private Pair<@AbsColIndex Integer, Double> scrollXToPixel(double targetX, ScrollGroup.Token token)
    {
        double lhs = Math.max(targetX, 0.0);
        for (int lhsCol = 0; lhsCol < currentColumns.get(); lhsCol++)
        {
            double lhsColWidth = getColumnWidth(lhsCol);
            if (lhsCol == currentColumns.get() - 1 && lhs > lhsColWidth)
            {
                // Clamp:
                lhs = lhsColWidth;
            }

            if (lhs <= lhsColWidth)
            {
                // Stop here, possibly clamping to RHS if needed:
                double clampedLHS = container.getWidth();
                for (int clampedLHSCol = currentColumns.get() - 1; clampedLHSCol >= 0; clampedLHSCol--)
                {
                    if (clampedLHS < lhsColWidth)
                    {
                        // We've found furthest place we could
                        // scroll to, so go to the leftmost out of
                        // furthest place, and ideal place:

                        // clampedLHS was from right, make it from left:
                        clampedLHS = lhsColWidth - clampedLHS;
                        if (clampedLHSCol < lhsCol || (clampedLHSCol == lhsCol && clampedLHS < lhs))
                            return new Pair<>(CellPosition.col(clampedLHSCol), -clampedLHS);
                        else
                            return new Pair<>(CellPosition.col(lhsCol), -lhs);
                    }
                    clampedLHS -= getColumnWidth(clampedLHSCol);
                }
                // If we get here, column widths are smaller than
                // container, so stay at left:
                return new Pair<>(CellPosition.col(0), 0.0);
            }
            lhs -= lhsColWidth;
        }
        // Should be impossible to reach here unless there are no columns:
        return new Pair<>(CellPosition.col(0), 0.0);
    }

    // This method should only be called by ScrollGroup, hence the magic token:
    // Immediately scrolls with no animation
    public Pair<@AbsRowIndex Integer, Double> scrollYToPixel(double y, ScrollGroup.Token token)
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
        int rows = getLastSelectableRowGlobal();
        // Don't let them scroll beyond bottom:
        if (bottomCell >= rows)
        {
            topCell = rows - 1 - (int)Math.floor(container.getHeight() / rowHeight);
            rowPixelOffset = container.getHeight() - (rows - topCell) * rowHeight;

            // But if whole table is visible, can't avoid scrolling beyond bottom;
            // avoid over-compensating by scrolling above top:
            if (topCell < 0)
            {
                topCell = 0;
                rowPixelOffset = 0.0;
            }
        }

        Pair<@AbsRowIndex Integer, Double> scrollDest = new Pair<>(CellPosition.row(topCell), rowPixelOffset);
        return scrollDest;
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

    private int getLastSelectableRowGlobal()
    {
        return 100; // TODO
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
        return (pos == null ? firstVisibleRowIndex : pos.getFirst()) * rowHeight - (pos == null ? firstVisibleRowOffset : pos.getSecond());
    }

    // If param is non-null, overrides our own data
    private double getCurrentScrollX(@Nullable Pair<@AbsColIndex Integer, Double> pos)
    {
        return sumColumnWidths(CellPosition.col(0), pos == null ? firstVisibleColumnIndex : pos.getFirst()) - (pos == null ? firstVisibleColumnOffset : pos.getSecond());
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
                        return gridArea.select(p);
                    }
                }
                return new EmptyCellSelection(p);
            },
            s -> s
        ));
    }

    public Node getNode()
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


    // The last actual display column (exclusive), including any needed for displaying smooth scrolling
    @AbsColIndex int getLastDisplayColExcl()
    {
        return Utility.minCol(currentColumns.get(), firstVisibleColumnIndex + CellPosition.col(visibleColumnCount) + Utility.maxCol(CellPosition.col(0), CellPosition.col(extraColsForSmoothScroll.get())));
    }

    // The first actual display column, including any needed for displaying smooth scrolling
    @AbsColIndex int getFirstDisplayCol()
    {
        return Utility.maxCol(CellPosition.col(0), firstVisibleColumnIndex + Utility.minCol(CellPosition.col(0), CellPosition.col(extraColsForSmoothScroll.get())));
    }

    // The last actual display row (exclusive), including any needed for displaying smooth scrolling
    @AbsRowIndex int getLastDisplayRowExcl()
    {
        return Utility.minRow(currentKnownRows.get(), firstVisibleRowIndex + CellPosition.row(visibleRowCount) + Utility.maxRow(CellPosition.row(0), CellPosition.row(extraRowsForSmoothScroll.get())));
    }

    // The first actual display row, including any needed for displaying smooth scrolling
    @AbsRowIndex int getFirstDisplayRow()
    {
        return Utility.maxRow(CellPosition.row(0), firstVisibleRowIndex + Utility.minRow(CellPosition.row(0), CellPosition.row(extraRowsForSmoothScroll.get())));
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
        selection.addListener(new ChangeListener<@Nullable CellSelection>()
        {
            @Override
            @OnThread(value = Tag.FXPlatform, ignoreParent = true)
            public void changed(ObservableValue<? extends @Nullable CellSelection> a, @Nullable CellSelection b, @Nullable CellSelection newVal)
            {
                onChange.consume(newVal);
                selection.removeListener(this);
            }
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

            EventHandler<? super @UnknownIfRecorded @UnknownKeyFor @UnknownIfValue @UnknownIfUserIndex @UnknownIfHelp @UnknownUnits MouseEvent> clickHandler = mouseEvent -> {

                @Nullable CellPosition cellPosition = getCellPositionAt(mouseEvent.getX(), mouseEvent.getY());
                
                if (cellPosition != null)
                {
                    boolean inTable = false;
                    // Go through each grid area and see if it contains the position:
                    for (GridArea gridArea : gridAreas)
                    {
                        if (gridArea.clicked(new Point2D(mouseEvent.getScreenX(), mouseEvent.getScreenY()), cellPosition))
                        {
                            inTable = true;
                            break;
                        }
                    }
                    if (!inTable)
                    {
                        // Belongs to no-one; we must handle it:
                        select(new EmptyCellSelection(cellPosition));
                        FXUtility.mouse(this).requestFocus();
                    }
                    redoLayout();
                }
                
                /*
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
                */
            };
            addEventFilter(MouseEvent.MOUSE_CLICKED, clickHandler);
            //addEventFilter(MouseEvent.MOUSE_PRESSED, clickHandler);
            //addEventFilter(MouseEvent.MOUSE_RELEASED, clickHandler);
            

            FXUtility.addChangeListenerPlatformNN(focusedProperty(), focused -> {
                if (!focused)
                {
                    //select(null);
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
            double x = firstVisibleColumnOffset;
            double y = firstVisibleRowOffset;

            // We may not need the +1, but play safe:
            int newNumVisibleRows = Math.min(currentKnownRows.get() - firstVisibleRowIndex, (int)Math.ceil(getHeight() / rowHeight) + 1);
            int newNumVisibleCols = 0;
            for (int column = firstVisibleColumnIndex; x < getWidth() && column < currentColumns.get(); column++)
            {
                newNumVisibleCols += 1;
                x += getColumnWidth(column);
            }
            VirtualGrid.this.visibleRowCount = newNumVisibleRows;
            VirtualGrid.this.visibleColumnCount = newNumVisibleCols;

            // This includes extra rows needed for smooth scrolling:
            @AbsRowIndex int firstDisplayRow = getFirstDisplayRow();
            @AbsRowIndex int lastDisplayRowExcl = getLastDisplayRowExcl();
            @AbsColIndex int firstDisplayCol = getFirstDisplayCol();
            @AbsColIndex int lastDisplayColExcl = getLastDisplayColExcl();

            VisibleDetails<@AbsColIndex Integer> columnBounds = new VisibleDetails<@AbsColIndex Integer>(firstDisplayCol, lastDisplayColExcl - CellPosition.col(1))
            {
                @Override
                public double getItemCoord(@AbsColIndex Integer itemIndex)
                {
                    return firstVisibleColumnOffset + sumColumnWidths(firstDisplayCol, itemIndex);
                }

                @Override
                @OnThread(Tag.FXPlatform)
                public Optional<@AbsColIndex Integer> getItemIndexForScreenPos(Point2D screenPos)
                {
                    Point2D localCoord = screenToLocal(screenPos);
                    double x = firstVisibleColumnOffset;
                    for (@AbsColIndex int i = firstVisibleColumnIndex; i < lastDisplayColExcl; i++)
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
                    return firstVisibleRowOffset + rowHeight * (itemIndex - firstDisplayRow);
                }

                @Override
                public Optional<@AbsRowIndex Integer> getItemIndexForScreenPos(Point2D screenPos)
                {
                    Point2D localCoord = screenToLocal(screenPos);
                    @AbsRowIndex int theoretical = CellPosition.row((int)Math.floor((localCoord.getY() - firstVisibleRowOffset) / rowHeight)) + firstDisplayRow;
                    if (firstVisibleRowIndex <= theoretical && theoretical < lastDisplayRowExcl)
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

        List<@AbsRowIndex Integer> rowSizes = Utility.<GridArea, @AbsRowIndex Integer>mapList(gridAreas, gridArea -> gridArea.getPosition().rowIndex + CellPosition.row(gridArea.getAndUpdateKnownRows(currentKnownRows.get() + MAX_EXTRA_ROW_COLS, this::updateSizeAndPositions)));
                
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
                    curPos = new CellPosition(curPos.rowIndex, openGridArea.getSecond().getPosition().columnIndex + CellPosition.col(openGridArea.getSecond().getColumnCount()));
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
            openGridAreas.removeIf(p -> p.getSecond().getPosition().columnIndex + p.getSecond().getColumnCount() <= cur.getSecond().getPosition().columnIndex);
            
            // Add ourselves to the open areas:
            openGridAreas.add(cur);
        }
        
        currentKnownRows.setValue(Utility.maxRow(MIN_ROWS, rowSizes.stream().max(Comparator.comparingInt(x -> x)).<@AbsRowIndex Integer>orElse(CellPosition.row(0))));
        container.redoLayout();
        updatingSizeAndPositions = false;
    }

    private boolean overlap(GridArea a, GridArea b)
    {
        int aLeftIncl = a.getPosition().columnIndex;
        int aRightIncl = a.getPosition().columnIndex + a.getColumnCount() - 1;
        int aTopIncl = a.getPosition().rowIndex;
        int aBottomIncl = a.getPosition().rowIndex + a.getCurrentKnownRows() - 1;
        
        int bLeftIncl = b.getPosition().columnIndex;
        int bRightIncl = b.getPosition().columnIndex + b.getColumnCount() - 1;
        int bTopIncl = b.getPosition().rowIndex;
        int bBottomIncl = b.getPosition().rowIndex + b.getCurrentKnownRows() - 1;
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
    }
}
