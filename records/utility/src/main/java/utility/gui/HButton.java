package utility.gui;

import javafx.scene.control.Button;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import utility.FXPlatformRunnable;

/**
 * A helper class which translates its label and lets you pass the action
 * and style-classes to the constructor.
 */
public class HButton extends Button
{
    public HButton(@LocalizableKey String msgKey, FXPlatformRunnable onAction, String... styleClasses)
    {
        super(TranslationUtility.getString(msgKey));
        setOnAction(e -> onAction.run());
        getStyleClass().addAll(styleClasses);
    }
}
