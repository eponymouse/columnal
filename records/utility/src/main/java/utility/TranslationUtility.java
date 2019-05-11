package utility;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.beans.value.ObservableStringValue;
import javafx.geometry.Bounds;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PopupControl;
import javafx.scene.control.Skin;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCharacterCombination;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyCombination.Modifier;
import javafx.scene.text.Text;
import javafx.stage.PopupWindow.AnchorLocation;
import log.Log;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.AcceleratorBaseVisitor;
import records.grammar.AcceleratorLexer;
import records.grammar.AcceleratorParser;
import records.grammar.AcceleratorParser.AcceleratorContext;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.FXPlatformSupplier;
import utility.Pair;
import utility.Utility;

import java.io.File;
import java.net.URL;
import java.util.*;

/**
 * Created by neil on 17/04/2017.
 */
@OnThread(Tag.Any)
public class TranslationUtility
{
    @OnThread(value = Tag.Any, requireSynchronized = true)
    private static @MonotonicNonNull List<ResourceBundle> resources;

    @OnThread(Tag.Any)
    private static synchronized @Nullable List<ResourceBundle> getResources()
    {
        if (resources == null)
        {
            try
            {
                // Each of these corresponds to a <name>_en.properties file in the resources directory
                resources = Arrays.asList(
                    ResourceBundle.getBundle("transformations"),
                    ResourceBundle.getBundle("expression"),
                    ResourceBundle.getBundle("function"),
                    ResourceBundle.getBundle("main"),
                    ResourceBundle.getBundle("newcolumn"),
                    ResourceBundle.getBundle("dataentry"),
                    ResourceBundle.getBundle("import")
                );
            }
            catch (MissingResourceException e)
            {
                Log.log(e);
                return null;
            }
        }
        return resources;
    }

    /**
     * Given a localization key (LHS in labels files), returns the localized value (RHS in labels files)
     *
     * If the key is not found, the key itself is returned as the string
     */
    @SuppressWarnings("i18n") // Because we return key if there's an issue
    @OnThread(Tag.Any)
    public static @Localized String getString(@LocalizableKey String key, String... values)
    {
        @Nullable List<ResourceBundle> res = getResources();
        if (res != null)
        {
            for (ResourceBundle r : res)
            {
                try
                {
                    @Nullable String local = r.getString(key);
                    if (local != null)
                    {
                        if (values.length == 0)
                            return local;
                        for (int i = 0; i < values.length; i++)
                        {
                            local = local.replace("$" + (i+1), values[i]);
                        }
                        return local;
                    }
                }
                catch (MissingResourceException e)
                {
                    // This is fine; just try the next one.
                }
            }
        }

        return key; // Best we can do, if we can't find the labels file.
    }

    @SuppressWarnings("i18n") // Because we return key if there's an issue
    @OnThread(Tag.Any)
    public static StyledString getStyledString(@LocalizableKey String key, String... values)
    {
        return StyledString.s(getString(key, values));
    }


}
