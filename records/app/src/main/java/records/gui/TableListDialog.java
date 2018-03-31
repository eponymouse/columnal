package records.gui;

import com.google.common.collect.ImmutableList;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.stage.Window;
import records.data.TableId;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.gui.InsertableReorderableDeletableListView;
import utility.gui.LightDialog;

import java.util.List;

// Shows an editable list of table ids
@OnThread(Tag.FXPlatform)
public class TableListDialog extends LightDialog<ImmutableList<TableId>>
{    
    protected TableListDialog(Window parent, List<TableId> originalItems)
    {
        super(parent);
        InsertableReorderableDeletableListView<TableId> tableList = new InsertableReorderableDeletableListView<TableId>(originalItems) {
            
        };            
        getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        getDialogPane().setContent(new BorderPane(tableList, new Label("Choose the tables to concatenate"), null, null, null));
        setResultConverter(bt -> {
            if (bt == ButtonType.OK)
                return tableList.getRealItems();
            else
                return null;
        });
    }
}
