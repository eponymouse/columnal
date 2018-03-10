package records.gui;

import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import log.Log;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.initialization.qual.Initialized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.gui.DataOrTransformChoice.DataOrTransform;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.FXPlatformRunnable;
import utility.Pair;
import utility.gui.FXUtility;
import utility.gui.GUI;
import utility.gui.LightDialog;
import utility.gui.TranslationUtility;

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
        Button transformButton = new ExplainedButton("new.transform", "new.transform.explanation", p -> {
            us.setResult(new Pair<>(p, DataOrTransform.TRANSFORM));
            close();
        });
        Button immediateDataButton = new ExplainedButton("new.data", "new.data.explanation", p -> {
            us.setResult(new Pair<>(p, DataOrTransform.DATA));
            close();
        });
        setOnShown(e -> immediateDataButton.requestFocus());
        VBox.setVgrow(immediateDataButton, Priority.ALWAYS);
        Button importFromFile = new ExplainedButton("import.file", "import.file.explanation", p -> {
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
        org.scenicview.ScenicView.show(getDialogPane().getScene());
    }

    public Optional<Pair<Point2D, DataOrTransform>> showAndWaitCentredOn(Point2D mouseScreenPos)
    {
        return super.showAndWaitCentredOn(mouseScreenPos, WIDTH, HEIGHT);
    }

    public static enum DataOrTransform {DATA, TRANSFORM, IMPORT_FILE };
    
    @OnThread(Tag.FXPlatform)
    private static class ExplainedButton extends Button
    {
        private @Nullable Point2D lastMouseScreenPos;
        
        public ExplainedButton(@LocalizableKey String titleKey, @LocalizableKey String explanationKey, FXPlatformConsumer<Point2D> onAction)
        {
            getStyleClass().add("explanation");
            setContentDisplay(ContentDisplay.BOTTOM);
            Label explanation = GUI.labelWrap(explanationKey, "explanation-button-explanation");
            explanation.setMaxWidth(WIDTH * 0.38);
            setGraphic(explanation);
            setText(TranslationUtility.getString(titleKey));
            setOnAction(e -> onAction.consume(getPos()));

            setMaxWidth(Double.MAX_VALUE);
            setMaxHeight(Double.MAX_VALUE);
            setPrefWidth(WIDTH * 0.45);
        }

        private Point2D getPos(@UnknownInitialization(Button.class) ExplainedButton this)
        {
            if (lastMouseScreenPos != null)
            {
                return lastMouseScreenPos;
            }
            else
            {
                Bounds bounds = localToScreen(getBoundsInLocal());
                return new Point2D((bounds.getMinX() + bounds.getMaxX()) / 2.0, (bounds.getMinY() + bounds.getMaxY()) / 2.0);
            }
        }
    }
}
