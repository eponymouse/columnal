package utility.gui;

import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.Dialog;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;

import java.util.Optional;

/**
 * A utility subclass of Dialog that has a minimal look: no window decorations, but 
 * a drop shadow to make it stand out from item beneath.
 */
@OnThread(Tag.FXPlatform)
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

    protected Optional<R> showAndWaitCentredOn(Point2D mouseScreenPos, double contentWidth, double contentHeight)
    {
        // 40 pixels for display padding for drop shadow, and rough guess of 40 more for button bar  
        setX(mouseScreenPos.getX() - (contentWidth + 40) *0.5);
        setY(mouseScreenPos.getY() - (contentHeight + 40 + 40) *0.5);
        return showAndWait();
    }

    protected void centreDialogButtons(@UnknownInitialization(Dialog.class) LightDialog<R> this)
    {
        // Hack!
        // Taken from https://stackoverflow.com/questions/36009764/how-to-align-ok-button-of-a-dialog-pane-in-javafx
        Region spacer = new Region();
        ButtonBar.setButtonData(spacer, ButtonBar.ButtonData.BIG_GAP);
        HBox.setHgrow(spacer, Priority.ALWAYS);
        getDialogPane().applyCss();
        HBox hbox = (HBox) getDialogPane().lookup(".container");
        hbox.getChildren().add(spacer);
    }
}
