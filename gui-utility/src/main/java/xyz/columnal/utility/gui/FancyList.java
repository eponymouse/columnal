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

package xyz.columnal.utility.gui;

import com.google.common.collect.ImmutableList;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener.Change;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.function.fx.FXPlatformConsumer;
import xyz.columnal.utility.function.fx.FXPlatformRunnable;
import xyz.columnal.utility.function.fx.FXPlatformSupplier;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.Utility;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A ListView which allows deletion of selected items using a little cross to the right (or pressing backspace/delete), which is
 * animated by sliding out the items.
 */
public abstract class FancyList<T extends @NonNull Object, CELL_CONTENT extends Node>
{
    private final VBox children = GUI.vbox("fancy-list-children");
    private final ObservableList<Cell> cells = FXCollections.observableArrayList();
    private final BitSet selection = new BitSet();
    private boolean dragging;
    private boolean hoverOverSelection = false;
    private final boolean allowReordering;
    private final boolean allowDeleting;
    private final ScrollPaneFill scrollPane = new FancyListScrollPane(children);
    private final BorderPane bottomPane = new BorderPane();
    private final @Nullable Button addButton;

    /**
     * 
     * @param initialItems
     * @param allowDeleting
     * @param allowReordering
     * @param makeNewItem If non-null, add button is shown and this
     *                    will be called to make a new value.  If null,
     *                    insertion is not allowed.
     */
    public FancyList(ImmutableList<T> initialItems, boolean allowDeleting, boolean allowReordering, boolean allowAdding)
    {
        this.allowDeleting = allowDeleting;
        this.allowReordering = allowReordering;
        scrollPane.getStyleClass().add("fancy-list");
        scrollPane.setVbarPolicy(ScrollBarPolicy.AS_NEEDED);
        scrollPane.setHbarPolicy(ScrollBarPolicy.NEVER);
        scrollPane.setAlwaysFitToWidth(true);
        children.setFillWidth(true);
        children.setOnKeyPressed(e -> {
            if (allowDeleting && (e.getCode() == KeyCode.DELETE || e.getCode() == KeyCode.BACK_SPACE))
            {
                FXUtility.keyboard(this).deleteCells(Utility.later(this).getSelectedCells());
            }
        });
        children.setOnMousePressed(e -> {
            if (e.getButton() == MouseButton.PRIMARY)
            {
                selection.clear();
                FXUtility.mouse(this).updateSelectionState();
            }
            e.consume();
        });
        children.setOnMouseDragged(e -> {
            if (allowReordering)
            {
                // TODO update the selection to include source item
                dragging = true;
                FXUtility.mouse(this).updateDragPreview(new Point2D(e.getX(), e.getY()));
            }
        });
        children.setOnMouseReleased(e -> {
            if (dragging && allowReordering)
            {
                int target = FXUtility.mouse(this).findClosestDragTarget(new Point2D(e.getX(), e.getY()));
                if (target != -1)
                {
                    FXUtility.mouse(this).dragSelectionTo(target);
                }
            }
            dragging = false;
            FXUtility.mouse(this).updateDragPreview(null);
        });
        for (T initialItem : initialItems)
        {
            cells.add(new Cell(initialItem, false));
        }
        if (allowAdding)
        {
            addButton = GUI.button("fancylist.add", () -> {
                addToEnd(null, true);
            });
            bottomPane.setCenter(addButton);
            BorderPane.setMargin(addButton, new Insets(6));
        }
        else
        {
            addButton = null;
        }
        bottomPane.getStyleClass().add("fancy-list-end");
        updateChildren();
    }

    private void dragSelectionTo(int target)
    {
        // We must adjust target for all the items we are removing:
        target -= selection.get(0, target).cardinality();
        List<Cell> selected = new ArrayList<>();
        
        for (int original = 0, adjusted = 0; original < cells.size(); original++) // Adjusted increment is conditional, in loop
        {
            if (selection.get(original))
            {
                selected.add(cells.remove(adjusted));
                // Don't increment adjusted as it already points to the next cell
            }
            else
            {
                adjusted += 1;
            }
        }
        cells.addAll(target, selected);
        selection.clear();
        // Or should we retain selection?
        updateChildren();
        updateSelectionState();
    }

    // If null is passed, we are not dragging, so turn off preview
    private void updateDragPreview(@Nullable Point2D childrenPoint)
    {
        if (children.getChildren().isEmpty())
            return;
        
        int index = childrenPoint == null ? -1 : findClosestDragTarget(childrenPoint);
        for (int i = 0; i < children.getChildren().size(); i++)
        {
            FXUtility.setPseudoclass(children.getChildren().get(i), "drag-target", i == index);
        }
    }

    private int findClosestDragTarget(Point2D childrenPoint)
    {
        for (int i = 0; i < children.getChildren().size(); i++)
        {
            Node item = children.getChildren().get(i);
            double rel = childrenPoint.getY() - item.getLayoutY();
            double itemHeight = item.getBoundsInParent().getHeight();
            if (0 <= rel && rel <= itemHeight / 2.0)
            {
                return i;
            }
            else if (rel <= itemHeight)
            {
                return Math.min(i + 1, children.getChildren().size() - 1);
            }
        }
        return -1;
    }

    public ObservableList<String> getStyleClass(@UnknownInitialization(FancyList.class) FancyList<T, CELL_CONTENT> this)
    {
        return scrollPane.getStyleClass();
    }

    private ImmutableList<Cell> getSelectedCells()
    {
        ImmutableList.Builder<Cell> builder = ImmutableList.builder();
        for (int i = 0; i < cells.size(); i++)
        {
            if (selection.get(i))
                builder.add(cells.get(i));
        }
        return builder.build();
    }

    /**
     * Given initial content (and a flag to indicate whether the item should be focused for editing),
     * gets a graphical item to display, and a function which
     * can be called in future to get the latest value from the
     * graphical component.
     */
    @OnThread(Tag.FXPlatform)
    protected abstract Pair<CELL_CONTENT, FXPlatformSupplier<T>> makeCellContent(Optional<T> initialContent, boolean editImmediately);

    private void deleteCells(List<Cell> selectedCells)
    {
        animateOutToRight(selectedCells, () -> {
            cleanup(selectedCells);
            cells.removeAll(selectedCells);
            updateChildren();
        });
    }
    
    protected void cleanup(List<Cell> cellsToCleanup)
    {
        // For overriding by child classes
    }

    private void animateOutToRight(List<Cell> cells, FXPlatformRunnable after)
    {
        SimpleDoubleProperty amount = new SimpleDoubleProperty(0);
        for (Cell cell : cells)
        {
            cell.translateXProperty().bind(amount);
        }
        
        Timeline t = new Timeline(new KeyFrame(Duration.millis(200),
                Utility.mapList(cells, c -> new KeyValue(amount, c.getWidth())).toArray(new KeyValue[0])));
        
        t.setOnFinished(e -> after.run());
        t.play();

    }

    //@RequiresNonNull({"bottomPane", "children", "cells", "scrollPane"})
    private void updateChildren(@UnknownInitialization(FancyList.class) FancyList<T, CELL_CONTENT> this)
    {
        for (int i = 0; i < cells.size(); i++)
        {
            boolean even = (i % 2) == 0;
            FXUtility.setPseudoclass(cells.get(i), "even", even);
            FXUtility.setPseudoclass(cells.get(i), "odd", !even);
        }
        ArrayList<Node> nodes = new ArrayList<>(this.cells);
        nodes.add(bottomPane);
        children.getChildren().setAll(nodes);
        scrollPane.fillViewport();
    }

    public ImmutableList<T> getItems()
    {
        return cells.stream().map(c -> c.value.get()).collect(ImmutableList.<T>toImmutableList());
    }
    
    protected Stream<Cell> streamCells(@UnknownInitialization(FancyList.class) FancyList<T, CELL_CONTENT> this)
    {
        return cells.stream();
    }

    public Region getNode()
    {
        return scrollPane;
    }

    protected void clearSelection()
    {
        selection.clear();
        updateSelectionState();
    }

    public void resetItems(List<@NonNull T> newItems)
    {
        cleanup(cells);
        cells.clear();
        cells.addAll(Utility.mapList(newItems, v -> new Cell(v, false)));
        updateChildren();
    }

    public @Nullable Node _test_scrollToItem(T scrollTo)
    {
        // Find the item:
        Node content = cells.stream().filter(cell -> Objects.equals(cell.value.get(), scrollTo)).findFirst().orElse(null);
        
        if (content != null)
        {
            double allContentHeight = children.getHeight();
            scrollPane.setVvalue((content.localToScene(content.getBoundsInLocal()).getMinY() - scrollPane.localToScene(scrollPane.getBoundsInLocal()).getMinY()) / allContentHeight);
        }
        
        return content;
    }

    public void focusAddButton()
    {
        if (addButton != null)
            addButton.requestFocus();
    }

    /**
     * Gets the nearest gap before/after a cell to the given scene X/Y position.  The first component
     * of the pair is the cell above (may be blank if at top of list), the second component is the
     * one below (ditto if at bottom).  Both parts may be blank if list is empty.
     * @return
     */
    /*
    @OnThread(Tag.FXPlatform)
    public Pair<@Nullable DeletableListCell, @Nullable DeletableListCell> getNearestGap(double sceneX, double sceneY)
    {
        // Y is in scene coords.  We set initial a pixel outside so that any items within bounds will "win" against them:
        Pair<Double, @Nullable DeletableListCell> nearestAbove = new Pair<>(localToScene(0.0, -1.0).getY(), null);
        Pair<Double, @Nullable DeletableListCell> nearestBelow = new Pair<>(localToScene(0.0, getHeight() + 1.0).getY(), null);

        for (WeakReference<DeletableListCell> ref : allCells)
        {
            @Nullable DeletableListCell cell = ref.get();
            if (cell != null && !cell.isEmpty())
            {
                Bounds sceneBounds = cell.localToScene(cell.getBoundsInLocal());
                if (Math.abs(sceneBounds.getMaxY() - sceneY) < Math.abs(nearestAbove.getFirst() - sceneY))
                {
                    nearestAbove = new Pair<>(sceneBounds.getMaxY(), cell);
                }

                if (Math.abs(sceneBounds.getMinY() - sceneY) < Math.abs(nearestBelow.getFirst() - sceneY))
                {
                    nearestBelow = new Pair<>(sceneBounds.getMinY(), cell);
                }
            }
        }

        // If nearest below is above nearest above, we picked both from last cell in the list; only return the nearest above:
        if (nearestBelow.getFirst() < nearestAbove.getFirst())
            return new Pair<>(nearestAbove.getSecond(), null);
        else
            return new Pair<>(nearestAbove.getSecond(), nearestBelow.getSecond());
    }*/

    @OnThread(Tag.FXPlatform)
    protected class Cell extends BorderPane
    {
        protected final SmallDeleteButton deleteButton;
        protected final CELL_CONTENT content;
        private final FXPlatformSupplier<T> value;
        
        public Cell(@Nullable T initialContent, boolean editImmediately)
        {
            getStyleClass().add("fancy-list-cell");
            deleteButton = new SmallDeleteButton();
            deleteButton.setOnAction(() -> {
                if (isSelected(this))
                {
                    // Delete all in selection
                    deleteCells(getSelectedCells());
                }
                else
                {
                    // Just delete this one
                    deleteCells(ImmutableList.<Cell>of(Utility.<Cell>later(this)));
                }
            });
            deleteButton.setOnHover(entered -> {
                if (isSelected(this))
                {
                    hoverOverSelection = entered;
                    // Set hover state on all (including us):
                    for (Cell selectedCell : getSelectedCells())
                    {
                        selectedCell.updateHoverState(hoverOverSelection);
                    }

                }
                // If not selected, nothing to do
            });
            setOnMousePressed(e -> {
                if (e.getButton() == MouseButton.PRIMARY)
                {
                    if (e.isShortcutDown())
                    {
                        selection.set(getIndex());
                    }
                    else if (e.isShiftDown())
                    {
                        if (!selection.get(getIndex()))
                        {
                            // Find the next earliest selection above us:
                            int prev = selection.previousSetBit(getIndex());
                            if (prev != -1)
                            {
                                selection.set(prev, getIndex() + 1);
                            }
                            else
                            {
                                // Next beyond us:
                                int next = selection.nextSetBit(getIndex());
                                if (next != -1)
                                {
                                    selection.set(getIndex(), next);
                                }
                                else
                                {
                                    // Just select us, then:
                                    selection.set(getIndex());
                                }
                            }
                        }
                    }
                    else
                    {
                        selection.clear();
                        selection.set(getIndex());
                    }
                    updateSelectionState();
                    e.consume();
                }
            });
            //deleteButton.visibleProperty().bind(deletable);
            setMargin(deleteButton, new Insets(0, 8, 0, 4));
            Pair<CELL_CONTENT, FXPlatformSupplier<T>> pair = makeCellContent(Optional.ofNullable(initialContent), editImmediately);
            this.content = pair.getFirst();
            this.value = pair.getSecond();
            setCenter(this.content);
            if (allowDeleting)
            {
                BorderPane.setAlignment(deleteButton, Pos.CENTER);
                setRight(deleteButton);
            }
        }

        private int getIndex(@UnknownInitialization Cell this)
        {
            return Utility.indexOfRef(cells, this);
        }

        @OnThread(Tag.FXPlatform)
        private void updateHoverState(boolean hovering)
        {
            pseudoClassStateChanged(PseudoClass.getPseudoClass("my_hover_sel"), hovering);
        }

        public CELL_CONTENT getContent()
        {
            return content;
        }
    }

    private void updateSelectionState()
    {
        for (int i = 0; i < cells.size(); i++)
        {
            FXUtility.setPseudoclass(cells.get(i), "selected", selection.get(i));
        }
    }

    private boolean isSelected(@UnknownInitialization Cell cell)
    {
        int index = Utility.indexOfRef(cells, cell);
        return index < 0 ? false : selection.get(index);
    }
    
    public void addToEnd(@UnknownInitialization(FancyList.class) FancyList<T, CELL_CONTENT> this, @Nullable T content, boolean editImmediately)
    {
        cells.add(new Cell(content, editImmediately));
        updateChildren();
    }
    
    public void setAddButtonText(@UnknownInitialization(FancyList.class) FancyList<T, CELL_CONTENT> this, @Localized String text)
    {
        if (addButton != null)
            addButton.setText(text);
    }
    
    protected void listenForCellChange(@UnknownInitialization(FancyList.class) FancyList<T, CELL_CONTENT> this, FXPlatformConsumer<Change<? extends Cell>> listener)
    {
        FXUtility.listen(cells, listener);
    }
    
    // True for empty, false when non-empty
    public void addEmptyListenerAndCallNow(FXPlatformConsumer<Boolean> callWithEmpty)
    {
        FXUtility.listenAndCallNow(cells, cellsValue -> callWithEmpty.consume(cellsValue.isEmpty()));
    }

    // Just used for testing, to get reference to parent.
    public class FancyListScrollPane extends ScrollPaneFill
    {
        public FancyListScrollPane(Node content)
        {
            super(content);
        }
        
        @OnThread(Tag.Any)
        @Pure
        public FancyList<T, CELL_CONTENT> _test_getList()
        {
            return FancyList.this;
        }
    }
}
