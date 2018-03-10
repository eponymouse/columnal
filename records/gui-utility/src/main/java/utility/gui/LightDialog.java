package utility.gui;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Dialog;
import javafx.scene.effect.DropShadow;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.StageStyle;
import javafx.stage.Window;

/**
 * A utility subclass of Dialog that has a minimal look: no window decorations, but 
 * a drop shadow to make it stand out from item beneath.
 */
public class LightDialog<R> extends Dialog<R>
{
    public LightDialog(Window parent)
    {
        initOwner(parent);
        initStyle(StageStyle.TRANSPARENT);
        initModality(Modality.WINDOW_MODAL);

        getDialogPane().getStylesheets().addAll(
            FXUtility.getStylesheet("general.css"),
            FXUtility.getStylesheet("dialogs.css")
        );
        getDialogPane().getStyleClass().add("light-dialog-pane");
        //getDialogPane().setEffect(new DropShadow());
        FXUtility.addChangeListenerPlatform(getDialogPane().contentProperty(), content -> {
            if (content != null)
            {
                content.getStyleClass().add("light-dialog-pane-content");
            }
        });
        Scene scene = getDialogPane().getScene();
        if (scene != null)
            scene.setFill(null);
    }
}
