package records.gui;

import com.google.common.collect.ImmutableList;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;
import records.data.ColumnId;
import records.data.TableManager;
import records.data.TableManager.TableMaker;
import records.transformations.HideColumns;
import records.transformations.HideColumnsPanel;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.gui.LightDialog;

/**
 * A dialog for editing a HideColumns transformation.
 */
@OnThread(Tag.FXPlatform)
public class HideColumnsDialog extends LightDialog<TableMaker>
{
    public HideColumnsDialog(Window parent, TableManager tableManager, HideColumns hideColumns)
    {
        super(parent);

        HideColumnsPanel hideColumnsPanel = new HideColumnsPanel(tableManager, hideColumns.getSource(), hideColumns.getHiddenColumns());
        getDialogPane().setContent(hideColumnsPanel.getNode());
        getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        
        setResultConverter(bt -> {
            if (bt == ButtonType.OK)
            {
                ImmutableList<ColumnId> newHidden = hideColumnsPanel.getHiddenColumns();
                TableMaker tableMaker = () -> new HideColumns(tableManager, hideColumns.getDetailsForCopy(), hideColumns.getSource(), newHidden);
                return tableMaker;
            }
            return null;
        });
    }
}
