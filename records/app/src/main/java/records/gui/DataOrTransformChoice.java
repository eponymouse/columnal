package records.gui;

import javafx.geometry.Point2D;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import records.gui.DataOrTransformChoice.DataOrTransform;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.gui.FXUtility;
import utility.gui.GUI;
import utility.gui.LightDialog;

import java.util.Optional;

/**
 * Returns a pair of mouse screen position, and choice
 */
@OnThread(Tag.FXPlatform)
public class DataOrTransformChoice extends LightDialog<Pair<Point2D, DataOrTransform>>
{
    private static final double WIDTH = 360;
    private static final double HEIGHT = 200;

    public DataOrTransformChoice(Window parent)
    {
        super(parent);
        
        @UnknownInitialization(Dialog.class) DataOrTransformChoice us = DataOrTransformChoice.this;
        Button transformButton = new ExplainedButton("new.transform", "new.transform.explanation", DataOrTransformChoice.WIDTH * 0.45, p -> {
            us.setResult(new Pair<>(p, DataOrTransform.TRANSFORM));
            close();
        });
        Button immediateDataButton = new ExplainedButton("new.data", "new.data.explanation", DataOrTransformChoice.WIDTH * 0.45, p -> {
            us.setResult(new Pair<>(p, DataOrTransform.DATA));
            close();
        });
        setOnShown(e -> immediateDataButton.requestFocus());
        VBox.setVgrow(immediateDataButton, Priority.ALWAYS);
        Button importFromFile = new ExplainedButton("import.file", "import.file.explanation", DataOrTransformChoice.WIDTH * 0.45, p -> {
            us.setResult(new Pair<>(p, DataOrTransform.IMPORT_FILE));
            close();
        });
        BorderPane content = new BorderPane(null, null, transformButton, null, GUI.vbox("new-button-list", immediateDataButton, importFromFile));
        FXUtility.forcePrefSize(content);
        content.setPrefWidth(WIDTH);
        content.setPrefHeight(HEIGHT);
        getDialogPane().setContent(content);
        getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL);
        setResultConverter(bt -> null);
        centreDialogButtons();
        //org.scenicview.ScenicView.show(getDialogPane().getScene());
    }

    public Optional<Pair<Point2D, DataOrTransform>> showAndWaitCentredOn(Point2D mouseScreenPos)
    {
        return super.showAndWaitCentredOn(mouseScreenPos, WIDTH, HEIGHT);
    }

    public static enum DataOrTransform {DATA, TRANSFORM, IMPORT_FILE };

}
