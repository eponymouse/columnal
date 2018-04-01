package records.gui;

import com.google.common.collect.ImmutableList;
import javafx.geometry.Point2D;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.stage.Window;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Table;
import records.data.TableId;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformSupplier;
import utility.gui.InsertableReorderableDeletableListView;
import utility.gui.LightDialog;

import java.util.List;

// Shows an editable list of table ids
@OnThread(Tag.FXPlatform)
public class TableListDialog extends LightDialog<ImmutableList<TableId>>
{    
    protected TableListDialog(View parent, List<TableId> originalItems)
    {
        super(parent.getWindow());
        FXPlatformSupplier<@Nullable TableId> pickNew = () -> 
            new PickTableDialog(parent, getMidpoint()).showAndWait().map(Table::getId).orElse(null);
        InsertableReorderableDeletableListView<TableId> tableList = new InsertableReorderableDeletableListView<TableId>(originalItems, pickNew);            
        getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        getDialogPane().setContent(new BorderPane(tableList, new Label("Choose the tables to concatenate"), null, null, null));
        setResultConverter(bt -> {
            if (bt == ButtonType.OK)
                return tableList.getRealItems();
            else
                return null;
        });
    }

    private Point2D getMidpoint(@UnknownInitialization(LightDialog.class) TableListDialog this)
    {
        return new Point2D(getX() + getWidth() / 2.0, getY() + getHeight() / 2.0);
    }
}
