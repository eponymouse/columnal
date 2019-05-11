package records.gui;

import annotation.identifier.qual.ExpressionIdentifier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Point2D;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.stage.Modality;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Table;
import records.data.TableId;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FXPlatformSupplier;
import utility.IdentifierUtility;
import utility.Pair;
import utility.Utility;
import utility.gui.ErrorableLightDialog;
import utility.gui.FXUtility;
import utility.gui.FancyList;
import utility.TranslationUtility;

// Shows an editable list of table ids
@OnThread(Tag.FXPlatform)
public class TableListDialog extends ErrorableLightDialog<ImmutableList<TableId>>
{
    private final View parent;
    private final ImmutableSet<Table> excludeTables;
    private final TableList tableList;

    public TableListDialog(View parent, Table destTable, ImmutableList<TableId> originalItems, Point2D lastScreenPos)
    {
        super(parent, true);
        initModality(Modality.NONE);
        setResizable(true);
        this.parent = parent;
        this.excludeTables = ImmutableSet.of(destTable);
        tableList = new TableList(originalItems);            
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
        
        setOnShowing(e -> {
            parent.enableTablePickingMode(lastScreenPos, excludeTables, t -> {
                tableList.pickTableIfEditing(t);
            });
        });
        setOnHiding(e -> {
            parent.disablePickingMode();
        });
        if (originalItems.isEmpty())
        {
            // runAfter to avoid focus stealing:
            FXUtility.runAfter(() -> tableList.addToEnd("", true));
        }
    }

    @Override
    protected @OnThread(Tag.FXPlatform) Either<@Localized String, ImmutableList<TableId>> calculateResult()
    {
        ImmutableList.Builder<TableId> r = ImmutableList.builder();
        for (String item : tableList.getItems())
        {
            @Nullable @ExpressionIdentifier String s = IdentifierUtility.asExpressionIdentifier(item);
            if (s != null)
                r.add(new TableId(s));
            else
                return Either.left(TranslationUtility.getString("edit.column.invalid.table.name"));
        }
        return Either.right(r.build());
    }

    @OnThread(Tag.FXPlatform)
    private class TableList extends FancyList<String, PickTablePane>
    {
        public TableList(ImmutableList<TableId> originalItems)
        {
            super(Utility.mapListI(originalItems, t -> t.getRaw()), true, true, () -> "");
            getStyleClass().add("table-list");
        }
        
        @Override
        protected Pair<PickTablePane, FXPlatformSupplier<String>> makeCellContent(@Nullable String original, boolean editImmediately)
        {
            if (original == null)
                original = "";
            SimpleObjectProperty<String> curValue = new SimpleObjectProperty<>(original);
            PickTablePane pickTablePane = new PickTablePane(parent, excludeTables, original, t -> {
                curValue.set(t.getId().getRaw());
                if (addButton != null)
                    addButton.requestFocus();
            });
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
            return new Pair<>(pickTablePane, curValue::get);
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
                addToEnd(t.getId().getRaw(), false);
            }
        }
    }
}
