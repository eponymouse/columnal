package records.gui;

import com.google.common.collect.ImmutableList;
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

    protected TableListDialog(View parent, ImmutableList<TableId> originalItems)
    {
        super(parent.getWindow());
        initModality(Modality.NONE);
        this.parent = parent;
        TableList tableList = new TableList(originalItems);            
        getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        Region tableListNode = tableList.getNode();
        tableListNode.setMinHeight(150.0);
        getDialogPane().setContent(new BorderPane(tableListNode, new Label("Choose the tables to concatenate"), null, null, null));
        setResultConverter(bt -> {
            if (bt == ButtonType.OK)
                return tableList.getItems();
            else
                return null;
        });
    }

    @OnThread(Tag.FXPlatform)
    private class TableList extends FancyList<TableId>
    {
        public TableList(ImmutableList<TableId> originalItems)
        {
            super(originalItems, true, true, true);
        }

        @Override
        protected Pair<Node, ObjectExpression<TableId>> makeCellContent(@Nullable TableId original)
        {
            if (original == null)
                original = new TableId("");
            SimpleObjectProperty<TableId> curValue = new SimpleObjectProperty<>(original);
            return new Pair<>(new PickTablePane(parent, original, t -> {
                curValue.set(t.getId());
                if (addButton != null)
                    addButton.requestFocus();
            }), curValue);
        }
    }
}
