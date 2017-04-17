package utility.gui;

import javafx.scene.control.*;
import javafx.scene.input.KeyCombination;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.Nullable;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformRunnable;
import utility.Pair;

/**
 * Created by neil on 17/04/2017.
 */
@OnThread(Tag.FXPlatform)
public class GUI
{
    public static Button button(@LocalizableKey String msgKey, FXPlatformRunnable onAction, String... styleClasses)
    {
        Button button = new Button(TranslationUtility.getString(msgKey));
        button.setOnAction(e -> onAction.run());
        button.getStyleClass().addAll(styleClasses);
        return button;
    }

    public static Label label(@LocalizableKey String msgKey, String... styleClasses)
    {
        Label label = new Label(TranslationUtility.getString(msgKey));
        label.getStyleClass().addAll(styleClasses);
        return label;
    }

    public static Menu menu(@LocalizableKey String menuNameKey, MenuItem... menuItems)
    {
        return new Menu(TranslationUtility.getString(menuNameKey), null, menuItems);
    }

    public static MenuItem menuItem(@LocalizableKey String menuItemKey, FXPlatformRunnable onAction)
    {
        @OnThread(Tag.FXPlatform) Pair<@Localized String, @Nullable KeyCombination> stringAndShortcut = TranslationUtility.getStringAndShortcut(menuItemKey);
        MenuItem item = new MenuItem(stringAndShortcut.getFirst());
        item.setOnAction(e -> onAction.run());
        if (stringAndShortcut.getSecond() != null)
            item.setAccelerator(stringAndShortcut.getSecond());
        return item;
    }
}
