package records.transformations;

import com.google.common.collect.ImmutableList;
import javafx.beans.binding.ObjectExpression;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import records.data.ColumnId;
import records.data.TableId;
import records.data.TableManager;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.gui.DeletableListView;
import utility.gui.FXUtility;
import utility.gui.FXUtility.DragHandler;
import utility.gui.GUI;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * A panel with two column expression lists, one on the left with columns to hide,
 * and one on the right with shown columns.  Drag and drop is allowed between the
 * two lists, and there's also a button to hide columns.
 */
@OnThread(Tag.FXPlatform)
public class HideColumnsPanel
{
    private final ListView<ColumnId> hiddenColumns;
    private final ListView<ColumnId> shownColumns;
    private final Pane pane;

    public HideColumnsPanel(TableManager mgr, ObjectExpression<@Nullable TableId> srcIdProperty, ImmutableList<ColumnId> initiallyHidden)
    {
        this.hiddenColumns = new DeletableListView<>(FXCollections.observableArrayList(initiallyHidden));
        this.shownColumns = //TransformationEditor.getColumnListView(mgr, srcIdProperty, hiddenColumns.getItems(), col -> {
            //addAllItemsToHidden(Collections.singletonList(col));
        //});
            new ListView<>();
        shownColumns.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        shownColumns.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER)
            {
                addAllItemsToHidden(shownColumns.getSelectionModel().getSelectedItems());
            }
        });
        // Don't need to remove here because we already filter by the items from hiddenColumns
        FXUtility.enableDragFrom(shownColumns, "ColumnId", TransferMode.MOVE);


        Button add = new Button("<< Hide");
        add.setMinWidth(Region.USE_PREF_SIZE);
        VBox addWrapper = new VBox(add);
        addWrapper.getStyleClass().add("add-column-wrapper");

        this.hiddenColumns.getStyleClass().add("hidden-columns-list-view");
        this.hiddenColumns.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2)
            {
                // Take copy to avoid problems with concurrent modifications:
                List<ColumnId> selectedItems = new ArrayList<>(hiddenColumns.getSelectionModel().getSelectedItems());
                if (!selectedItems.isEmpty())
                {
                    hiddenColumns.getItems().removeAll(selectedItems);
                }
            }
        });

        FXUtility.enableDragTo(hiddenColumns, Collections.singletonMap(FXUtility.getTextDataFormat("ColumnId"), new DragHandler()
        {
            @Override
            @SuppressWarnings("unchecked")
            public @OnThread(Tag.FXPlatform) boolean dragEnded(Dragboard db, Point2D pointInScene)
            {
                @Nullable Object content = db.getContent(FXUtility.getTextDataFormat("ColumnId"));
                if (content != null && content instanceof List)
                {
                    addAllItemsToHidden((List<ColumnId>) content);
                    return true;
                }
                return false;
            }
        }));

        add.setOnAction(e -> {
            ObservableList<ColumnId> selectedItems = shownColumns.getSelectionModel().getSelectedItems();
            addAllItemsToHidden(selectedItems);
            //sortHiddenColumns();
        });

        GridPane gridPane = new GridPane();

        GridPane.setHgrow(shownColumns, Priority.ALWAYS);
        GridPane.setHgrow(hiddenColumns, Priority.ALWAYS);
        gridPane.add(GUI.label("transformEditor.hide.hiddenColumns"), 0, 0);
        gridPane.add(GUI.label("transformEditor.hide.srcColumns"), 2, 0);
        gridPane.add(hiddenColumns, 0, 1);
        gridPane.add(addWrapper, 1, 1);
        gridPane.add(shownColumns, 2, 1);
        gridPane.getStyleClass().add("hide-columns-lists");
        this.pane = gridPane;
    }


    @OnThread(Tag.FXPlatform)
    @SuppressWarnings("initialization")
    public void addAllItemsToHidden(@UnknownInitialization(Object.class) HideColumnsPanel this, List<ColumnId> items)
    {
        for (ColumnId selected : items)
        {
            if (!hiddenColumns.getItems().contains(selected))
            {
                hiddenColumns.getItems().add(selected);
            }
        }
        ObservableList<ColumnId> srcList = shownColumns.getItems();
        hiddenColumns.getItems().sort(Comparator.<ColumnId, Pair<Integer, ColumnId>>comparing(col -> {
            // If it's in the original, we sort by original position
            // Otherwise we put it at the top (which will be -1 in original, which
            // works out neatly) and sort by name.
            int srcIndex = srcList.indexOf(col);
            return new Pair<Integer, ColumnId>(srcIndex, col);
        }, Pair.comparator()));
    }

    public Node getNode()
    {
        return pane;
    }

    public ImmutableList<ColumnId> getHiddenColumns()
    {
        return ImmutableList.copyOf(hiddenColumns.getItems());
    }
}
