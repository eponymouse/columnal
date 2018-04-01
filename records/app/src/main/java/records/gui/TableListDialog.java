package records.gui;

import com.google.common.collect.ImmutableList;
import javafx.beans.binding.BooleanBinding;
import javafx.geometry.Point2D;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
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
import utility.Utility;
import utility.gui.FXUtility;
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

    protected TableListDialog(View parent, List<TableId> originalItems)
    {
        super(parent.getWindow());
        this.parent = parent;
        InsertableReorderableDeletableListView<TableId> tableList = new TableListView(originalItems);            
        getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        getDialogPane().setContent(new BorderPane(tableList, new Label("Choose the tables to concatenate"), null, null, null));
        setResultConverter(bt -> {
            if (bt == ButtonType.OK)
                return tableList.getRealItems();
            else
                return null;
        });
    }

    @OnThread(Tag.FXPlatform)
    private class TableListView extends InsertableReorderableDeletableListView<TableId>
    {
        public TableListView(List<TableId> originalItems)
        {
            super(originalItems, () -> new TableId(""));
            setEditable(true);
            FXUtility.listen(getItems(), c -> {
                Log.logStackTrace("Items: " + Utility.listToString(getItems()));
            });
        }

        @Override
        protected DeletableListCell makeCell()
        {
            return new TableIdCell();
        }

        @Override
        protected String valueToString(Optional<TableId> item)
        {
            return item.map(t -> t.getRaw()).orElse("");
        }

        @OnThread(Tag.FXPlatform)
        private class TableIdCell extends IRDListCell
        {
            private final BooleanBinding notEmpty = emptyProperty().not();
            private final PickTablePane pickTablePane;
            
            public TableIdCell()
            {
                pickTablePane = new PickTablePane(parent, t -> {
                    Utility.later(this).commitEdit(Optional.of(t.getId()));
                });
            }

            @Override
            @OnThread(Tag.FXPlatform)
            protected void setNormalContent()
            {
                //FXUtility.onceNotNull(pickTablePane.sceneProperty(), s -> pickTablePane.focusEntryField());
                pickTablePane.setContent(Optional.ofNullable(getItem()).flatMap(x -> x).orElse(null));
                contentPane.setCenter(pickTablePane);
                contentPane.setRight(deleteButton);
                contentPane.setTop(null);
                contentPane.setBottom(null);
                contentPane.setLeft(null);
            }

            @Override
            @OnThread(value = Tag.FXPlatform, ignoreParent = true)
            public void startEdit()
            {
                super.startEdit();
                pickTablePane.focusEntryField();
            }

            @Override
            @OnThread(value = Tag.FXPlatform, ignoreParent = true)
            public void commitEdit(Optional<TableId> newValue)
            {
                super.commitEdit(newValue);
                if (newValue.isPresent())
                    getItems().set(getIndex(), newValue);
            }

            @Override
            @OnThread(value = Tag.FXPlatform, ignoreParent = true)
            public void cancelEdit()
            {
                super.cancelEdit();
                //pickTablePane.cancelEdit();
            }
        }

        @Override
        protected void addAtEnd()
        {
            int newIndex = getItems().size() - 1;
            super.addAtEnd();
            edit(newIndex);
        }
    }
}
