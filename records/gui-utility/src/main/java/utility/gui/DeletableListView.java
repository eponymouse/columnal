package utility.gui;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.Utility;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A ListView which allows deletion of selected items using a little cross to the right (or pressing backspace/delete), which is
 * animated by sliding out the items.
 */
public class DeletableListView<T> extends ListView<T>
{
    // Keep track of all cells for the purposes of finding bounds:
    private final List<WeakReference<DeletableListCell>> allCells = new ArrayList<>();
    // Have to keep manual track of the selected cells.  ListView only tells us selected items,
    // but can't map that back to selected cells:
    private final ObservableList<DeletableListCell> selectedCells = FXCollections.observableArrayList();
    private boolean hoverOverSelection = false;

    public DeletableListView(ObservableList<T> items)
    {
        super(items);
        setCellFactory(lv -> {
            DeletableListCell cell = Utility.later(this).makeCell();
            allCells.add(new WeakReference<>(cell));
            return cell;
        });
        // Default is to allow multi-select.  Caller can always change later:
        getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DELETE || e.getCode() == KeyCode.BACK_SPACE)
            {
                deleteSelection();
            }
        });
    }

    public DeletableListView()
    {
        this(FXCollections.observableArrayList());
    }

    // For overriding in subclasses:
    @OnThread(Tag.FXPlatform)
    protected DeletableListCell makeCell()
    {
        return new DeletableListCell();
    }

    // For overriding by subclasses:
    @OnThread(Tag.FXPlatform)
    protected boolean canDeleteValue(@UnknownInitialization(ListView.class) DeletableListView<T> this, T value)
    {
        return true;
    }

    // For overriding by subclasses:
    @OnThread(Tag.FXPlatform)
    protected String valueToString(@NonNull T item)
    {
        return item.toString();
    }

    @OnThread(Tag.FXPlatform)
    private void deleteSelection(@UnknownInitialization(ListView.class) DeletableListView<T> this)
    {
        ArrayList<DeletableListCell> cols = new ArrayList<>(selectedCells);
        List<T> selectedItems = new ArrayList<>(getSelectionModel().getSelectedItems());
        selectedItems.removeIf(v -> !canDeleteValue(v));
        getSelectionModel().clearSelection();
        DeletableListCell.animateOutToRight(cols, () -> getItems().removeAll(selectedItems));
    }

    /**
     * Gets the nearest gap before/after a cell to the given scene X/Y position.  The first component
     * of the pair is the cell above (may be blank if at top of list), the second component is the
     * one below (ditto if at bottom).  Both parts may be blank if list is empty.
     * @return
     */
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
    }

    @OnThread(Tag.FXPlatform)
    public void forAllCells(FXPlatformConsumer<DeletableListCell> action)
    {
        for (WeakReference<DeletableListCell> ref : allCells)
        {
            @Nullable DeletableListCell cell = ref.get();
            if (cell != null)
            {
                action.consume(cell);
            }
        }
    }

    public class DeletableListCell extends SlidableListCell<T>
    {
        private final SmallDeleteButton button;
        private final Label label;
        private final BooleanProperty deletable = new SimpleBooleanProperty(true);

        @SuppressWarnings("initialization")
        public DeletableListCell()
        {
            getStyleClass().add("deletable-list-cell");
            button = new SmallDeleteButton();
            button.setOnAction(() -> {
                if (isSelected())
                {
                    // Delete all in selection
                    deleteSelection();
                }
                else
                {
                    // Just delete this one
                    animateOutToRight(Collections.singletonList(this), () -> getItems().remove(getItem()));
                }
            });
            button.setOnHover(entered -> {
                if (isSelected())
                {
                    hoverOverSelection = entered;
                    // Set hover state on all (including us):
                    for (DeletableListCell selectedCell : selectedCells)
                    {
                        selectedCell.updateHoverState(hoverOverSelection);
                    }

                }
                // If not selected, nothing to do
            });
            button.visibleProperty().bind(deletable);
            label = new Label("");
            BorderPane.setAlignment(label, Pos.CENTER_LEFT);
            BorderPane borderPane = new BorderPane(label, null, button, null, null);
            borderPane.getStyleClass().add("deletable-list-cell-content");
            setGraphic(borderPane);
        }

        @OnThread(Tag.FXPlatform)
        private void updateHoverState(boolean hovering)
        {
            pseudoClassStateChanged(PseudoClass.getPseudoClass("my_hover_sel"), hovering);
        }

        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        public void updateSelected(boolean selected)
        {
            if (selected)
            {
                selectedCells.add(this);
                updateHoverState(hoverOverSelection);
            }
            else
            {
                selectedCells.remove(this);
                updateHoverState(false);
            }
            super.updateSelected(selected);
        }

        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        protected void updateItem(@Nullable T item, boolean empty)
        {
            if (empty || item == null)
            {
                label.setText("");
                deletable.set(false);
            }
            else
            {
                label.setText(valueToString(item));
                deletable.set(canDeleteValue(item));
            }

            super.updateItem(item, empty);
        }
    }
}
