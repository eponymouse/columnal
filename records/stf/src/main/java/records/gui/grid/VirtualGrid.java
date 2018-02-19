package records.gui.grid;

import annotation.help.qual.UnknownIfHelp;
import annotation.qual.UnknownIfValue;
import annotation.recorded.qual.UnknownIfRecorded;
import annotation.userindex.qual.UnknownIfUserIndex;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollBar;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;
import log.Log;
import org.checkerframework.checker.initialization.qual.UnderInitialization;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.UnknownKeyFor;
import org.fxmisc.wellbehaved.event.EventPattern;
import org.fxmisc.wellbehaved.event.InputMap;
import org.fxmisc.wellbehaved.event.Nodes;
import records.data.CellPosition;
import records.gui.grid.RectangularTableCellSelection.TableSelectionLimits;
import records.gui.grid.VirtualGridSupplier.VisibleDetails;
import records.gui.stable.ScrollBindable;
import records.gui.stable.ScrollGroup;
import records.gui.stable.ScrollResult;
import records.gui.stable.VirtScrollStrTextGrid.ScrollLock;
import records.gui.stf.StructuredTextField;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformBiConsumer;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@OnThread(Tag.FXPlatform)
public abstract class VirtualGrid implements ScrollBindable
{
    private final List<VirtualGridSupplier<? extends Node>> nodeSuppliers = new ArrayList<>();
    private final List<GridArea> gridAreas = new ArrayList<>();
    
    private final Container container;
    private final ScrollGroup scrollGroup;
    private static final int MAX_EXTRA_ROW_COLS = 12;
    private final ScrollBar hBar;
    private final ScrollBar vBar;
    private final CreateTableButtonSupplier createTableButtonSupplier;
    // Used as a sort of lock on updating the scroll bars to prevent re-entrant updates:
    private boolean settingScrollBarVal = false;

    // The first index logically visible.  This is not actually necessarily the same
    // as first really-visible, if we are currently doing some smooth scrolling:
    private int firstVisibleColumnIndex;
    private int firstVisibleRowIndex;
    // Offset of top visible cell.  Always -rowHeight <= y <= 0
    private double firstVisibleColumnOffset;
    private double firstVisibleRowOffset;
    private int visibleRowCount;
    private int visibleColumnCount;
    // How many extra rows to show off-screen, to account for scrolling (when actual display can lag logical display).
    // Negative means we need them left/above, positive means we need them below/right:
    private final IntegerProperty extraRowsForSmoothScroll = new SimpleIntegerProperty(0);
    private final IntegerProperty extraColsForSmoothScroll = new SimpleIntegerProperty(0);
    
    private static final int MIN_ROWS = 10;
    private static final int MIN_COLS = 10;
    private final IntegerProperty currentKnownRows = new SimpleIntegerProperty(MIN_ROWS);
    private final IntegerProperty currentColumns = new SimpleIntegerProperty(MIN_COLS);

    // Package visible to let sidebars access it
    static final double rowHeight = 24;
    static final double defaultColumnWidth = 100;

    private final Map<Integer, Double> customisedColumnWidths = new HashMap<>();

    private final ObjectProperty<@Nullable CellSelection> selection = new SimpleObjectProperty<>(null);

    private final BooleanProperty atLeftProperty = new SimpleBooleanProperty(false);
    private final BooleanProperty atRightProperty = new SimpleBooleanProperty(false);
    private final BooleanProperty atTopProperty = new SimpleBooleanProperty(false);
    private final BooleanProperty atBottomProperty = new SimpleBooleanProperty(false);
    
    // How many extra rows to show off-screen, to account for scrolling (when actual display can lag logical display).
    // Negative means we need them left/above, positive means we need them below/right:
    private final IntegerProperty extraRowsForScrolling = new SimpleIntegerProperty(0);
    private final IntegerProperty extraColsForScrolling = new SimpleIntegerProperty(0);
    // null means the grid and immediately contained cells don't have focus
    private @Nullable Pair<FocusType, CellPosition> focus;

    private static enum FocusType {EMPTY};
    
    public VirtualGrid()
    {
        this.createTableButtonSupplier = new CreateTableButtonSupplier();
        nodeSuppliers.add(createTableButtonSupplier);
        this.hBar = new ScrollBar();
        this.vBar = new ScrollBar();
        this.container = new Container();
        this.scrollGroup = new ScrollGroup(
                FXUtility.mouse(this)::scrollLayoutXBy, targetX -> {
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
    }
    
    private @Nullable CellPosition getCellPositionAt(double x, double y)
    {
        int rowIndex;
        int colIndex;
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
        rowIndex = (int) Math.floor(y / rowHeight) + firstVisibleRowIndex;
        if (rowIndex >= getLastSelectableRowGlobal())
            return null;
        return new CellPosition(rowIndex, colIndex);
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
            //FXUtility.setPseudoclass(glass, "top-shadow", !atTop);
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
            //FXUtility.setPseudoclass(glass, "left-shadow", !atLeft);
        }
        container.redoLayout();
    }

    @Override
    public @OnThread(Tag.FXPlatform) void updateClip()
    {
        container.updateClip();
    }

    // This scrolls just the layout, without smooth scrolling
    private ScrollResult scrollLayoutXBy(double x, ScrollGroup.Token token)
    {
        double prevScroll = getCurrentScrollX(null);
        Pair<Integer, Double> pos = scrollXToPixel(prevScroll + x, token);
        return new ScrollResult(getCurrentScrollX(pos) - prevScroll, pos);
    }

    // This scrolls just the layout, without smooth scrolling
    // Returns the amount that we actually scrolled by, which will either
    // be given parameter, or otherwise it will have been clamped because we tried
    // to scroll at the very top or very bottom
    private ScrollResult scrollLayoutYBy(double y, ScrollGroup.Token token)
    {
        double prevScroll = getCurrentScrollY(null);
        Pair<Integer, Double> pos = scrollYToPixel(prevScroll + y, token);
        return new ScrollResult(getCurrentScrollY(pos) - prevScroll, pos);
    }

    // This method should only be called by ScrollGroup, hence the magic token:
    private Pair<Integer, Double> scrollXToPixel(double targetX, ScrollGroup.Token token)
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
                            return new Pair<>(clampedLHSCol, -clampedLHS);
                        else
                            return new Pair<>(lhsCol, -lhs);
                    }
                    clampedLHS -= getColumnWidth(clampedLHSCol);
                }
                // If we get here, column widths are smaller than
                // container, so stay at left:
                return new Pair<>(0, 0.0);
            }
            lhs -= lhsColWidth;
        }
        // Should be impossible to reach here unless there are no columns:
        return new Pair<>(0, 0.0);
    }

    // This method should only be called by ScrollGroup, hence the magic token:
    // Immediately scrolls with no animation
    public Pair<Integer, Double> scrollYToPixel(double y, ScrollGroup.Token token)
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

        Pair<Integer, Double> scrollDest = new Pair<>(topCell, rowPixelOffset);
        return scrollDest;
    }

    /**
     * Adds the column widths for any column index C where startColIndexIncl <= C < endColIndexExcl
     * If startColIndexIncl >= endColIndexExcl, zero will be returned.
     */
    private double sumColumnWidths(int startColIndexIncl, int endColIndexExcl)
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
        return Math.max(0, sumColumnWidths(0, currentColumns.get())  - container.getWidth());
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
    private double getCurrentScrollY(@Nullable Pair<Integer, Double> pos)
    {
        return (pos == null ? firstVisibleRowIndex : pos.getFirst()) * rowHeight - (pos == null ? firstVisibleRowOffset : pos.getSecond());
    }

    // If param is non-null, overrides our own data
    private double getCurrentScrollX(@Nullable Pair<Integer, Double> pos)
    {
        return sumColumnWidths(0, pos == null ? firstVisibleColumnIndex : pos.getFirst()) - (pos == null ? firstVisibleColumnOffset : pos.getSecond());
    }

    // Selects cell so that you can navigate around with keyboard
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

    public Node getNode()
    {
        return container;
    }

    public void positionOrAreaChanged(GridArea child)
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
    int getLastDisplayColExcl()
    {
        return Math.min(currentColumns.get(), firstVisibleColumnIndex + visibleColumnCount + Math.max(0, extraColsForSmoothScroll.get()));
    }

    // The first actual display column, including any needed for displaying smooth scrolling
    int getFirstDisplayCol()
    {
        return Math.max(0, firstVisibleColumnIndex + Math.min(0, extraColsForSmoothScroll.get()));
    }

    // The last actual display row (exclusive), including any needed for displaying smooth scrolling
    int getLastDisplayRowExcl()
    {
        return Math.min(currentKnownRows.get(), firstVisibleRowIndex + visibleRowCount + Math.max(0, extraRowsForSmoothScroll.get()));
    }

    // The first actual display row, including any needed for displaying smooth scrolling
    int getFirstDisplayRow()
    {
        return Math.max(0, firstVisibleRowIndex + Math.min(0, extraRowsForSmoothScroll.get()));
    }

    public void addNodeSupplier(VirtualGridSupplier<?> cellSupplier)
    {
        nodeSuppliers.add(cellSupplier);
        container.redoLayout();
    }

    @OnThread(Tag.FXPlatform)
    private class Container extends Region
    {
        private final Rectangle clip;

        public Container()
        {
            getStyleClass().add("virt-grid");
            // Need this for when JavaFX looks for a default focus target:
            setFocusTraversable(true);

            clip = new Rectangle();
            setClip(clip);

            FXUtility.addChangeListenerPlatformNN(widthProperty(), w -> {updateHBar(); redoLayout();});
            FXUtility.addChangeListenerPlatformNN(heightProperty(), h -> {updateVBar(); redoLayout();});

            EventHandler<? super @UnknownIfRecorded @UnknownKeyFor @UnknownIfValue @UnknownIfUserIndex @UnknownIfHelp MouseEvent> clickHandler = mouseEvent -> {

                @Nullable CellPosition cellPosition = getCellPositionAt(mouseEvent.getX(), mouseEvent.getY());
                
                if (cellPosition != null)
                {
                    // Go through each grid area and see if it contains the position:
                    for (GridArea gridArea : gridAreas)
                    {
                        if (gridArea.contains(cellPosition))
                        {
                            return;
                        }
                    }
                    // Belongs to no-one; we must handle it:
                    focus = new Pair<>(FocusType.EMPTY, cellPosition);
                    FXUtility.mouse(this).requestFocus();
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
            addEventFilter(MouseEvent.MOUSE_PRESSED, clickHandler);
            addEventFilter(MouseEvent.MOUSE_RELEASED, clickHandler);

            // Filter because we want to steal it from the cells themselves:
            addEventFilter(ScrollEvent.ANY, scrollEvent -> {
                scrollGroup.requestScroll(scrollEvent);
                scrollEvent.consume();
            });

            FXUtility.addChangeListenerPlatformNN(focusedProperty(), focused -> {
                if (!focused)
                {
                    focus = null;
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
                select(focusedCellPos.move(extendSelection, rows, columns));
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
            int firstDisplayRow = getFirstDisplayRow();
            int lastDisplayRowExcl = getLastDisplayRowExcl();
            int firstDisplayCol = getFirstDisplayCol();
            int lastDisplayColExcl = getLastDisplayColExcl();

            VisibleDetails columnBounds = new VisibleDetails(firstDisplayCol, lastDisplayColExcl - 1)
            {
                @Override
                public double getItemCoord(int itemIndex)
                {
                    return firstVisibleColumnOffset + sumColumnWidths(firstDisplayCol, itemIndex);
                }
            };
            VisibleDetails rowBounds = new VisibleDetails(firstDisplayRow, lastDisplayRowExcl - 1)
            {
                @Override
                public double getItemCoord(int itemIndex)
                {
                    return firstVisibleRowOffset + rowHeight * (itemIndex - firstDisplayRow);
                }
            };

            for (VirtualGridSupplier<? extends Node> nodeSupplier : nodeSuppliers)
            {
                nodeSupplier.layoutItems(getChildren(), rowBounds, columnBounds);
            }
            Log.debug("Children: " + getChildren().size());
            
            Container.this.updateClip();
            requestLayout();
        }

        private void updateClip(@UnknownInitialization(Region.class) Container this)
        {
            if (clip != null && scrollGroup != null)
            {
                clip.setX(-getTranslateX());
                clip.setY(-getTranslateY());
                clip.setWidth(getWidth());
                clip.setHeight(getHeight());
                scrollGroup.updateClip();
            }
        }
    }

    private void updateSizeAndPositions()
    {
        // Three things to do:
        //   - Get each grid area to update its known size (which may involve calling us back)
        //   - Check for overlaps between tables, and reshuffle if needed
        //   - Update our known overall grid size

        List<Integer> rowSizes = Utility.mapList(gridAreas, gridArea -> gridArea.getPosition().rowIndex + gridArea.updateKnownRows(currentKnownRows.get() + MAX_EXTRA_ROW_COLS, this::updateSizeAndPositions));
        
        // TODO check for overlaps and do reshuffle
        
        currentKnownRows.setValue(Math.max(MIN_ROWS, rowSizes.stream().mapToInt(x -> x).max().orElse(0)));
        container.redoLayout();
    }
    
    private void activateCell(@Nullable CellSelection cellPosition)
    {
        // TODO
    }
    
    public void addGridArea(GridArea gridArea)
    {
        gridAreas.add(gridArea);
        gridArea.addedToGrid(this);
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
        
        @Override
        void layoutItems(List<Node> containerChildren, VisibleDetails rowBounds, VisibleDetails columnBounds)
        {
            if (button == null)
            {
                button = new Button("+++");
                button.setFocusTraversable(false);
                Point2D[] lastMousePos = new Point2D[1];
                button.addEventFilter(MouseEvent.ANY, e -> {
                    lastMousePos[0] = new Point2D(e.getScreenX(), e.getScreenY());
                });
                button.setOnAction(e -> {
                    if (focus != null)
                    {
                        // Offer to create a table at that location, but we need to ask data or transform, if it's not the first table:
                        createTable(focus.getSecond(), lastMousePos[0]);
                    }
                });
                containerChildren.add(button);
            }

            @Nullable Pair<FocusType, CellPosition> curFocus = VirtualGrid.this.focus;
            if (curFocus != null && curFocus.getFirst() == FocusType.EMPTY)
            {
                button.setVisible(true);
                double x = columnBounds.getItemCoord(curFocus.getSecond().columnIndex);
                double y = rowBounds.getItemCoord(curFocus.getSecond().rowIndex);
                button.resizeRelocate(
                    x,
                    y,
                    columnBounds.getItemCoord(curFocus.getSecond().columnIndex + 1) - x,
                    rowBounds.getItemCoord(curFocus.getSecond().rowIndex + 1) - y
                );
            }
            else
            {
                button.setVisible(false);
            }
        }
    }

    protected abstract void createTable(CellPosition cellPosition, Point2D mouseScreenPos);
}
