package records.gui;

import javafx.geometry.Point2D;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.stage.Modality;
import javafx.stage.Window;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import records.data.Table;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.gui.FXUtility;
import utility.gui.LightDialog;

@OnThread(Tag.FXPlatform)
public class PickTableDialog extends LightDialog<Table>
{
    public PickTableDialog(View view, Point2D lastScreenPos)
    {
        super(view.getWindow());
        initModality(Modality.NONE);
        
        getDialogPane().setContent(new Label("TEMP"));

        getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL);
        setResultConverter(bt -> null);
        centreDialogButtons();
        
        setOnShowing(e -> {
            view.enableTablePickingMode(lastScreenPos, t -> {
                // We shouldn't need the mouse call here, I think this is a checker framework bug:
                FXUtility.mouse(this).setResult(t);
                close();
            });
        });
        setOnHiding(e -> {
            view.disableTablePickingMode();
        });
    }
}
