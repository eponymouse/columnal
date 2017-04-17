package utility.gui;

import javafx.scene.control.Label;
import org.checkerframework.checker.i18n.qual.LocalizableKey;

/**
 * A Label helper class that translates its label key and allows
 * you to specify style classes in the constructor
 */
public class HLabel extends Label
{
    public HLabel(@LocalizableKey String msgKey, String... styleClasses)
    {
        super(TranslationUtility.getString(msgKey));
        getStyleClass().addAll(styleClasses);
    }
}
