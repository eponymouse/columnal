package records.gui;

import javafx.geometry.Point2D;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
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
    private static final double WIDTH = 380;
    private static final double HEIGHT = 260;

    public DataOrTransformChoice(Window parent, boolean transformIsValid)
    {
        super(parent);
        
        @UnknownInitialization(Dialog.class) DataOrTransformChoice us = DataOrTransformChoice.this;
        Button transformButton = new ExplainedButton("new.transform", "new.transform.explanation", DataOrTransformChoice.WIDTH * 0.45, p -> {
            us.setResult(new Pair<>(p, DataOrTransform.TRANSFORM));
            close();
        });
        transformButton.setDisable(!transformIsValid);
        VBox.setVgrow(transformButton, Priority.ALWAYS);
        Button checkButton = new ExplainedButton("new.check", "new.check.explanation", DataOrTransformChoice.WIDTH * 0.45, p -> {
            us.setResult(new Pair<>(p, DataOrTransform.CHECK));
            close();
        });
        checkButton.setDisable(!transformIsValid);

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
        Button importFromLink = new ExplainedButton("import.url", "import.url.explanation", DataOrTransformChoice.WIDTH * 0.45, p -> {
            us.setResult(new Pair<>(p, DataOrTransform.IMPORT_URL));
            close();
        });
        
        Label explanation = new Label("Tables are either plain data (left) or transformations of other tables (right)");
        explanation.getStyleClass().add("new-explanation");
        explanation.setWrapText(true);
        
        BorderPane content = new BorderPane(null, explanation, GUI.vbox("new-button-list", transformButton, checkButton), null, GUI.vbox("new-button-list", immediateDataButton, importFromFile, importFromLink));
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

    public static enum DataOrTransform {DATA, IMPORT_FILE, IMPORT_URL, TRANSFORM, CHECK };

}
