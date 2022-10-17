/*
 * Columnal: Safer, smoother data table processing.
 * Copyright (c) Neil Brown, 2016-2020, 2022.
 *
 * This file is part of Columnal.
 *
 * Columnal is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Columnal is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for 
 * more details.
 *
 * You should have received a copy of the GNU General Public License along 
 * with Columnal. If not, see <https://www.gnu.org/licenses/>.
 */

package xyz.columnal.gui;

import annotation.units.AbsColIndex;
import annotation.units.AbsRowIndex;
import annotation.units.GridAreaRowIndex;
import com.google.common.collect.ImmutableMap;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.HPos;
import javafx.geometry.Point2D;
import javafx.geometry.VPos;
import javafx.scene.Cursor;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Window;
import xyz.columnal.log.Log;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.CellPosition;
import xyz.columnal.data.GridComment;
import xyz.columnal.data.GridComment.GridCommentDisplayBase;
import xyz.columnal.data.TableManager;
import xyz.columnal.gui.dtf.DisplayDocument;
import xyz.columnal.gui.dtf.DocumentTextField;
import xyz.columnal.gui.grid.CellSelection;
import xyz.columnal.gui.grid.GridArea;
import xyz.columnal.gui.grid.RectangleBounds;
import xyz.columnal.gui.grid.VirtualGridSupplier.ItemState;
import xyz.columnal.gui.grid.VirtualGridSupplier.ViewOrder;
import xyz.columnal.gui.grid.VirtualGridSupplier.VisibleBounds;
import xyz.columnal.gui.grid.VirtualGridSupplierFloating.FloatingItem;
import xyz.columnal.styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.fx.FXPlatformRunnable;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Workers;
import xyz.columnal.utility.Workers.Priority;
import xyz.columnal.utility.gui.FXUtility;

import java.util.Optional;

@OnThread(Tag.FXPlatform)
public final class GridCommentDisplay extends GridArea implements GridCommentDisplayBase
{
    private final TableManager tableManager;
    private final GridComment comment;
    private final FloatingItem<DocumentTextField> floatingItem;

    public GridCommentDisplay(TableManager tableManager, GridComment comment)
    {
        this.tableManager = tableManager;
        this.comment = comment;
        setPosition(comment.getPosition());
        this.floatingItem = new FloatingItem<DocumentTextField>(ViewOrder.STANDARD_CELLS)
        {
            private @Nullable CellPosition currentDragBottomRight;
            
            @Override
            protected Optional<BoundingBox> calculatePosition(VisibleBounds visibleBounds)
            {
                double x = visibleBounds.getXCoord(comment.getPosition().columnIndex);
                double y = visibleBounds.getYCoord(comment.getPosition().rowIndex);
                double ex = visibleBounds.getXCoordAfter(currentDragBottomRight != null ? currentDragBottomRight.columnIndex : comment.getBottomRight().columnIndex);
                double ey = visibleBounds.getYCoordAfter(currentDragBottomRight != null ? currentDragBottomRight.rowIndex : comment.getBottomRight().rowIndex);
                DocumentTextField node = getNode();
                if (node != null)
                    node.setIdealWidth(ex - x);
                return Optional.of(new BoundingBox(x, y, ex - x, ey - y));
            }

            @Override
            protected DocumentTextField makeCell(VisibleBounds visibleBounds)
            {
                DocumentTextField textField = new DocumentTextField(null) {
                    boolean pressedOnResize;
                    
                    @Override
                    @OnThread(Tag.FXPlatform)
                    protected void mouseEvent(MouseEvent e)
                    {
                        if (e.getEventType() == MouseEvent.MOUSE_PRESSED && e.getButton() == MouseButton.PRIMARY && getCursor() == Cursor.SE_RESIZE)
                        {
                            pressedOnResize = true;
                            return;
                        }
                        else
                        {
                            if (pressedOnResize && e.getEventType() == MouseEvent.MOUSE_RELEASED && currentDragBottomRight != null)
                            {
                                comment.setBottomRight(currentDragBottomRight);
                                // Resync the bottom right:
                                FXUtility.mouse(GridCommentDisplay.this).getAndUpdateBottomRow(AbsRowIndex.ONE, () -> {});
                                withParent_(g -> g.redoLayoutAfterScroll());
                                FXUtility.setPseudoclass(this, "resizing", false);
                                currentDragBottomRight = null;
                            }
                            else if (e.getEventType() == MouseEvent.MOUSE_PRESSED || e.getEventType() == MouseEvent.MOUSE_RELEASED)
                            {
                                pressedOnResize = false;
                                currentDragBottomRight = null;
                                FXUtility.setPseudoclass(this, "resizing", false);
                                withParent_(g -> g.redoLayoutAfterScroll());
                            }
                        }

                        if (pressedOnResize && e.getEventType() == MouseEvent.MOUSE_DRAGGED)
                        {
                            FXUtility.setPseudoclass(this, "resizing", true);
                            withParent_(g -> {
                                VisibleBounds vis = g.getVisibleBounds();
                                Optional<CellPosition> nearest = vis.getNearestTopLeftToScreenPos(new Point2D(e.getScreenX(), e.getScreenY()), HPos.CENTER, VPos.CENTER);
                                nearest.ifPresent(pos -> {
                                    currentDragBottomRight = new CellPosition(Utility.maxRow(getPosition().rowIndex, pos.rowIndex - AbsRowIndex.ONE), Utility.maxCol(getPosition().columnIndex, pos.columnIndex - AbsColIndex.ONE));
                                    Log.debug("Pos: " + getPosition() + " current drag: " + currentDragBottomRight);
                                    g.redoLayoutAfterScroll();
                                });
                            });
                            return;
                        }
                        
                        super.mouseEvent(e);
                    }
                };
                textField.getStyleClass().add("grid-comment-field");
                textField.setDocument(new DisplayDocument(comment.getContent())
                {
                    @Override
                    public void setAndSave(String content)
                    {
                        comment.setContent(content);
                    }
                });
                return textField;
            }

            @Override
            public @Nullable Pair<ItemState, @Nullable StyledString> getItemState(CellPosition cellPosition, Point2D screenPos)
            {
                if (new RectangleBounds(comment.getPosition(), comment.getBottomRight()).contains(cellPosition))
                {
                    DocumentTextField node = getNode();
                    if (node != null)
                    {
                        Bounds screenBounds = node.localToScreen(node.getBoundsInLocal());
                        if (!node.isFocused() &&
                            screenBounds.getMaxX() - 8 <= screenPos.getX() && screenPos.getX() <= screenBounds.getMaxX() &&
                            screenBounds.getMaxY() - 8 <= screenPos.getY() && screenPos.getY() <= screenBounds.getMaxY())
                        {
                            node.setCursor(Cursor.SE_RESIZE);
                        }
                        else
                        {
                            node.setCursor(null);
                        }
                    }
                    return new Pair<>(node != null && node.isFocused() ? ItemState.EDITING : ItemState.DIRECTLY_CLICKABLE, null);
                }
                return null;
            }

            @Override
            public void keyboardActivate(CellPosition cellPosition)
            {
                DocumentTextField node = super.getNode();
                if (node != null)
                    node.requestFocus();
            }
        };
        comment.setDisplay(this);
    }

    @Override
    @OnThread(Tag.FXPlatform)
    protected void updateKnownRows(@GridAreaRowIndex int checkUpToRowIncl, FXPlatformRunnable updateSizeAndPositions)
    {
        // Nothing to do
    }

    @Override
    protected CellPosition recalculateBottomRightIncl()
    {
        return comment.getBottomRight();
    }

    @Override
    public @Nullable CellSelection getSelectionForSingleCell(CellPosition cellPosition)
    {
        if (new RectangleBounds(comment.getPosition(), comment.getBottomRight()).contains(cellPosition))
            return new CommentSelection(cellPosition.columnIndex, cellPosition.rowIndex);
        else
            return null;
    }

    @Override
    public String getSortKey()
    {
        return "";
    }

    public FloatingItem<?> getFloatingItem()
    {
        return floatingItem;
    }

    public void requestFocus()
    {
        floatingItem.keyboardActivate(getPosition());
    }

    private class CommentSelection implements CellSelection
    {
        // Although we select the whole comment, if they move out up/down, we stay
        // in the column they entered from:
        private final @AbsColIndex int column;
        private final @AbsRowIndex int row;

        private CommentSelection(@AbsColIndex int column, @AbsRowIndex int row)
        {
            this.column = column;
            this.row = row;
        }

        @Override
        public void doCopy()
        {
            Clipboard.getSystemClipboard().setContent(ImmutableMap.of(DataFormat.PLAIN_TEXT, comment.getContent()));
        }

        @Override
        public void doPaste()
        {
            String content = Clipboard.getSystemClipboard().getString();
            if (content != null && !content.isEmpty())
                comment.setContent(content);
        }

        @Override
        public void doDelete()
        {
            Workers.onWorkerThread("Deleting comment", Priority.SAVE, () -> tableManager.removeComment(comment));
        }

        @Override
        public CellPosition getActivateTarget()
        {
            return getPosition();
        }

        @Override
        public @Nullable CellSelection extendTo(CellPosition cellPosition)
        {
            // Can't extend comment selections:
            return null;
        }

        @Override
        public CellSelection atHome(boolean extendSelection)
        {
            return this;
        }

        @Override
        public CellSelection atEnd(boolean extendSelection)
        {
            return this;
        }

        @Override
        public Either<CellPosition, CellSelection> move(boolean extendSelection, int byRows, int byColumns)
        {
            CellPosition start = new CellPosition(byRows < 0 ? getPosition().rowIndex : (byRows > 0 ? getBottomRightIncl().rowIndex : row), byColumns < 0 ? getPosition().columnIndex : (byColumns > 0 ? getBottomRightIncl().columnIndex : column));
            return Either.left(start.offsetByRowCols(byRows, byColumns));
        }

        @Override
        public CellPosition positionToEnsureInView()
        {
            return getPosition();
        }

        @Override
        public RectangleBounds getSelectionDisplayRectangle()
        {
            return new RectangleBounds(getPosition(), getBottomRightIncl());
        }

        @Override
        public boolean isExactly(CellPosition cellPosition)
        {
            return cellPosition.equals(getPosition()) && getBottomRightIncl().equals(cellPosition);
        }

        @Override
        public boolean includes(@UnknownInitialization(GridArea.class) GridArea area)
        {
            return GridCommentDisplay.this == area;
        }

        @Override
        public void gotoRow(Window parent)
        {
            // Not applicable
        }

        @Override
        public void notifySelected(boolean selected, boolean animateFlash)
        {
            // Nothing to do
        }
    }
}
