package records.gui.table;

import com.google.common.collect.ImmutableList;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import xyz.columnal.data.ColumnId;
import xyz.columnal.data.TableId;
import xyz.columnal.data.TableManager;
import xyz.columnal.transformations.HideColumnsPanel;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.gui.FXUtility;

@OnThread(Tag.FXPlatform)
class CustomColumnDisplayDialog extends Dialog<ImmutableList<ColumnId>>
{
    private final HideColumnsPanel hideColumnsPanel;

    public CustomColumnDisplayDialog(TableManager mgr, TableId tableId, ImmutableList<ColumnId> initialHidden)
    {
        this.hideColumnsPanel = new HideColumnsPanel(mgr, tableId, initialHidden);
        getDialogPane().getStylesheets().addAll(FXUtility.getSceneStylesheets());
        getDialogPane().setContent(hideColumnsPanel.getNode());
        getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        setResultConverter(bt -> {
            if (bt == ButtonType.OK)
                return hideColumnsPanel.getHiddenColumns();
            else
                return null;
        });
    }
}
