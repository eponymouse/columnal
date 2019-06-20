package records.gui;

import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.ResourceUtility;
import utility.Utility;
import utility.gui.GUI;
import utility.TranslationUtility;

import java.net.URL;

/**
 * A button with a title, an explanation, and optionally an image.
 */
@OnThread(Tag.FXPlatform)
class ExplainedButton extends Button
{
    private @Nullable Point2D lastMouseScreenPos;
    
    private ExplainedButton(double buttonWidth, FXPlatformConsumer<Point2D> onAction)
    {
        getStyleClass().add("explanation");
        setOnAction(e -> onAction.consume(getPos()));
        setMaxWidth(Double.MAX_VALUE);
        setMaxHeight(Double.MAX_VALUE);
        setPrefWidth(buttonWidth);

        setOnMouseEntered(e -> {
            lastMouseScreenPos = new Point2D(e.getScreenX(), e.getScreenY());
        });
        setOnMouseMoved(e -> {
            lastMouseScreenPos = new Point2D(e.getScreenX(), e.getScreenY());
        });
        setOnMouseExited(e -> {
            lastMouseScreenPos = null;
        });
    }

    /**
     * Make a button with text title, and explanation text
     * @param titleKey Key for the title
     * @param explanationKey Key for the explanation text
     * @param buttonWidth The width of the button
     * @param onAction The action to run when the button is clicked.
     */
    public ExplainedButton(@LocalizableKey String titleKey, @LocalizableKey String explanationKey, double buttonWidth, FXPlatformConsumer<Point2D> onAction)
    {
        this(buttonWidth, onAction);
        GUI.<ExplainedButton>addIdClass(Utility.<ExplainedButton>later(this), titleKey);
        setAccessibleText(TranslationUtility.getString(titleKey) + "\n" + TranslationUtility.getString(explanationKey));
        setContentDisplay(ContentDisplay.BOTTOM);
        Label explanation = GUI.labelWrap(explanationKey, "explanation-button-explanation");
        explanation.setMaxWidth(buttonWidth * 0.85);
        setGraphic(explanation);
        setText(TranslationUtility.getString(titleKey));
    }

    /**
     * Make a button with text title, and explanation text
     * @param titleKey Key for the title
     * @param explanationKey Key for the explanation text
     * @param buttonWidth The width of the button
     * @param onAction The action to run when the button is clicked.
     */
    public ExplainedButton(@LocalizableKey String titleKey, @LocalizableKey String explanationKey, String imageFileName, double buttonWidth, FXPlatformConsumer<Point2D> onAction)
    {
        this(buttonWidth, onAction);
        String titleText = TranslationUtility.getString(titleKey);
        // Set text in case the screen reader uses that even when graphic only.
        setAccessibleText(titleText + "\n" + TranslationUtility.getString(explanationKey));
        setContentDisplay(ContentDisplay.BOTTOM);
        setText(titleText);
        // Safe because just modifies style classes:
        GUI.<ExplainedButton>addIdClass(Utility.<ExplainedButton>later(this), titleKey);
        setAlignment(Pos.TOP_CENTER);
        Label explanation = GUI.labelWrap(explanationKey, "explanation-button-explanation");
        explanation.setMaxWidth(buttonWidth * 0.85);
        @Nullable ImageView imageView = null;
        URL imageURL = ResourceUtility.getResource(imageFileName);
        if (imageURL != null)
        {
            imageView = new ImageView(imageURL.toExternalForm());
            // Original image size for the previews is 100 pixels:
            imageView.setFitWidth(100.0);
            imageView.setPreserveRatio(true);
            imageView.setSmooth(true);
            BorderPane.setMargin(imageView, new Insets(0, 0, 15, 0));
        }
        
        setGraphic(new BorderPane(imageView, null, null, explanation, null));
        
    }

    private Point2D getPos(@UnknownInitialization(Button.class)ExplainedButton this)
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

    @Override
    @OnThread(Tag.FX)
    public Orientation getContentBias()
    {
        return Orientation.HORIZONTAL;
    }
}
