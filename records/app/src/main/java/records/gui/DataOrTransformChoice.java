package records.gui;

import javafx.geometry.Point2D;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.stage.Modality;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import log.Log;
import org.checkerframework.checker.initialization.qual.Initialized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import records.gui.DataOrTransformChoice.DataOrTransform;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.gui.FXUtility;
import utility.gui.GUI;

import java.util.Optional;

@OnThread(Tag.FXPlatform)
public class DataOrTransformChoice extends Dialog<DataOrTransform>
{
    private static final double WIDTH = 300;
    private static final double HEIGHT = 200;

    public DataOrTransformChoice(Window parent)
    {
        initOwner(parent);
        initStyle(StageStyle.UNDECORATED);
        initModality(Modality.WINDOW_MODAL);
        @UnknownInitialization(Dialog.class) DataOrTransformChoice us = DataOrTransformChoice.this;
        Button transformButton = GUI.button("new.transform", () -> {
            us.setResult(DataOrTransform.TRANSFORM);
            close();
        });
        Button dataButton = GUI.button("new.data", () -> {
            us.setResult(DataOrTransform.DATA);
            close();
        });
        for (@Initialized Button button : new @NonNull Button[]{transformButton, dataButton})
        {
            button.setMaxWidth(Double.MAX_VALUE);
            button.setMaxHeight(Double.MAX_VALUE);
            button.setPrefWidth(WIDTH * 0.4);
        }
        BorderPane content = new BorderPane(null, new Label("Explanation"), transformButton, null, dataButton);
        FXUtility.forcePrefSize(content);
        content.setPrefWidth(WIDTH);
        content.setPrefHeight(HEIGHT);
        getDialogPane().setContent(content);
        getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL);
        setResultConverter(bt -> null);
    }

    public Optional<DataOrTransform> showAndWaitCentredOn(Point2D mouseScreenPos)
    {
        // This only centres the content pane on the mouse, not the whole dialog
        // (which also includes the cancel button), but that works well enough:
        setX(mouseScreenPos.getX() - WIDTH*0.5);
        setY(mouseScreenPos.getY() - HEIGHT*0.5);
        return showAndWait();
    }

    public static enum DataOrTransform {DATA, TRANSFORM};
}
