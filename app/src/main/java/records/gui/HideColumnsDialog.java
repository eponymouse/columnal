package records.gui;

import com.google.common.collect.ImmutableList;
import javafx.scene.control.ButtonType;
import xyz.columnal.data.ColumnId;
import xyz.columnal.data.TableManager;
import xyz.columnal.data.RenameOnEdit;
import xyz.columnal.transformations.HideColumns;
import xyz.columnal.transformations.HideColumnsPanel;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.gui.DimmableParent;
import xyz.columnal.utility.gui.LightDialog;

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
