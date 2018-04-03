package records.gui;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.stage.Modality;
import javafx.stage.Window;
import log.Log;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Table;
import records.data.TableId;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformSupplier;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;
import utility.gui.FancyList;
import utility.gui.GUI;
import utility.gui.InsertableReorderableDeletableListView;
import utility.gui.LightDialog;

import java.util.List;
import java.util.Optional;

// Shows an editable list of table ids
@OnThread(Tag.FXPlatform)
public class TableListDialog extends LightDialog<ImmutableList<TableId>>
{
    private final View parent;
    private final ImmutableSet<Table> excludeTables;

    protected TableListDialog(View parent, Table destTable, ImmutableList<TableId> originalItems, Point2D lastScreenPos)
    {
        super(parent.getWindow());
        initModality(Modality.NONE);
        setResizable(true);
        this.parent = parent;
        this.excludeTables = ImmutableSet.of(destTable);
        TableList tableList = new TableList(originalItems);            
        getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        Region tableListNode = tableList.getNode();
        tableListNode.setMinWidth(200.0);
        tableListNode.setMinHeight(150.0);
        tableListNode.setPrefWidth(250.0);
        tableListNode.setPrefHeight(200.0);
        getDialogPane().setContent(new BorderPane(tableListNode, new Label("Choose the tables to concatenate"), null, null, null));
        getDialogPane().getStylesheets().addAll(
            FXUtility.getStylesheet("general.css"),
            FXUtility.getStylesheet("dialogs.css")
        );
        getDialogPane().getStyleClass().add("table-list-dialog");
        setResultConverter(bt -> {
            if (bt == ButtonType.OK)
                return tableList.getItems();
            else
                return null;
        });
        
        setOnShowing(e -> {
            parent.enableTablePickingMode(lastScreenPos, excludeTables, t -> {
                tableList.pickTableIfEditing(t);
            });
        });
        setOnHiding(e -> {
            parent.disableTablePickingMode();
        });
    }

    @OnThread(Tag.FXPlatform)
    private class TableList extends FancyList<TableId, PickTablePane>
    {
        public TableList(ImmutableList<TableId> originalItems)
        {
            super(originalItems, true, true, true);
            getStyleClass().add("table-list");
        }
        
        @Override
        protected Pair<PickTablePane, ObjectExpression<TableId>> makeCellContent(@Nullable TableId original, boolean editImmediately)
        {
            if (original == null)
                original = new TableId("");
            SimpleObjectProperty<TableId> curValue = new SimpleObjectProperty<>(original);
            PickTablePane pickTablePane = new PickTablePane(parent, excludeTables, original, t -> {
                curValue.set(t.getId());
                if (addButton != null)
                    addButton.requestFocus();
            });
            pickTablePane.showLabelOnlyWhenFocused();
            FXUtility.addChangeListenerPlatformNN(pickTablePane.currentlyEditing(), ed -> {
                if (ed)
                {
                    clearSelection();
                }
            });
            if (editImmediately)
            {
                FXUtility.runAfter(() -> pickTablePane.focusEntryField());
            }
            return new Pair<>(pickTablePane, curValue);
        }

        public void pickTableIfEditing(Table t)
        {
            // This is a bit of a hack.  The problem is that clicking the table removes the focus
            // from the edit field, so we can't just ask if the edit field is focused.  Tracking who
            // the focus transfers from/to seems a bit awkward, so we just use a time-based system,
            // where if they were very recently (200ms) editing a field, fill in that field with the table.
            // If they weren't recently editing a field, we append to the end of the list.
            long curTime = System.currentTimeMillis();
            PickTablePane curEditing = streamCells()
                .map(cell -> cell.getContent())
                .filter(p -> p.lastEditTimeMillis() > curTime - 200L).findFirst().orElse(null);
            if (curEditing != null)
            {
                curEditing.setContent(t);
                if (addButton != null)
                    addButton.requestFocus();
            }
            else
            {
                // Add to end:
                addToEnd(t.getId(), false);
            }
        }
    }
}
