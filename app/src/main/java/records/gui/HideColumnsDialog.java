package records.gui;

import com.google.common.collect.ImmutableList;
import javafx.scene.control.ButtonType;
import records.data.ColumnId;
import records.data.TableManager;
import records.data.RenameOnEdit;
import records.transformations.HideColumns;
import records.transformations.HideColumnsPanel;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.gui.DimmableParent;
import utility.gui.LightDialog;

/**
 * A dialog for editing a HideColumns transformation.
 */
@OnThread(Tag.FXPlatform)
public class HideColumnsDialog extends LightDialog<ImmutableList<ColumnId>>
{
    public HideColumnsDialog(DimmableParent parent, TableManager tableManager, HideColumns hideColumns)
    {
        super(parent);

        HideColumnsPanel hideColumnsPanel = new HideColumnsPanel(tableManager, hideColumns.getSrcTableId(), hideColumns.getHiddenColumns());
        getDialogPane().setContent(hideColumnsPanel.getNode());
        getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        getDialogPane().lookupButton(ButtonType.OK).getStyleClass().add("ok-button");
        
        setResultConverter(bt -> {
            if (bt == ButtonType.OK)
            {
                return hideColumnsPanel.getHiddenColumns();
            }
            return null;
        });
    }
}
