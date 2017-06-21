package utility.gui;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.beans.value.ObservableStringValue;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PopupControl;
import javafx.scene.control.Skin;
import javafx.scene.input.KeyCharacterCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyCombination.Modifier;
import javafx.scene.text.Text;
import javafx.stage.PopupWindow.AnchorLocation;
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
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.FXPlatformSupplier;
import utility.Pair;
import utility.Utility;

import java.util.*;

/**
 * Created by neil on 17/04/2017.
 */
@OnThread(Tag.FXPlatform)
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
                    ResourceBundle.getBundle("dataentry")
                );
            }
            catch (MissingResourceException e)
            {
                Utility.log(e);
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

    @SuppressWarnings("i18n") // Because we return original if there's an issue
    @OnThread(Tag.FXPlatform)
    public static Pair<@Localized String, @Nullable KeyCombination> getStringAndShortcut(@LocalizableKey String key)
    {

        String original = getString(key);
        int atIndex = original.indexOf("@");
        if (atIndex != -1)
        {
            @Nullable KeyCombination shortcut = null;
            try
            {
                shortcut = Utility.<@Nullable KeyCombination, AcceleratorParser>parseAsOne(original.substring(atIndex + 1).trim(), AcceleratorLexer::new, AcceleratorParser::new, p ->
                {
                    return new AcceleratorBaseVisitor<@Nullable KeyCombination>()
                    {
                        @Override
                        public @Nullable KeyCombination visitAccelerator(AcceleratorContext ctx)
                        {
                            List<Modifier> modifiers = new ArrayList<>();
                            if (ctx.SHORTCUT_MODIFIER() != null)
                                modifiers.add(KeyCombination.SHORTCUT_DOWN);
                            if (ctx.ALT_MODIFIER() != null)
                                modifiers.add(KeyCombination.ALT_DOWN);
                            if (ctx.SHIFT_MODIFIER() != null)
                                modifiers.add(KeyCombination.SHIFT_DOWN);
                            return new KeyCharacterCombination(ctx.KEY().getText(), modifiers.toArray(new Modifier[0]));
                        }
                    }.visit(p.accelerator());
                });
            }
            catch (InternalException | UserException e)
            {
                // No need to tell the user, it's an internal error:
                Utility.log(e);
            }
            return new Pair<>(original.substring(0, atIndex), shortcut);
        }
        else
            return new Pair<>(original, null);
    }

    /**
     * In the simple case, takes a localization key and returns a singleton list with a Text
     * item containg the corresponding localization value + "\n"
     *
     * If the localization value contains any substitutions like ${date}, then that is transformed
     * into a hover-over definition and/or hyperlink, and thus multiple nodes may be returned.
     */
    @SuppressWarnings("i18n") // Because of substring processing
    public static List<Node> makeTextLine(@LocalizableKey String key, String styleclass)
    {
        ArrayList<Node> r = new ArrayList<>();
        @Localized String original = getString(key);
        @Localized String remaining = original;
        for (int subst = remaining.indexOf("${"); subst != -1; subst = remaining.indexOf("${"))
        {
            int endSubst = remaining.indexOf("}", subst);
            String target = remaining.substring(subst + 2, endSubst);
            String before = remaining.substring(0, subst);
            r.add(new Text(before));
            Hyperlink link = new Hyperlink(target);
            FXUtility.addChangeListenerPlatformNN(link.hoverProperty(), new FXPlatformConsumer<Boolean>()
                {
                    private @Nullable PopupControl info;
                    @Override
                    public @OnThread(Tag.FXPlatform) void consume(Boolean hovering)
                    {
                        if (hovering && info == null)
                        {
                            info = new PopupControl();
                            PopupControl ctrl = info;
                            Label label = new Label("More info on " + target);
                            ctrl.setSkin(new Skin<PopupControl>()
                            {
                                @Override
                                @OnThread(Tag.FX)
                                public PopupControl getSkinnable()
                                {
                                    return ctrl;
                                }

                                @Override
                                @OnThread(Tag.FX)
                                public Node getNode()
                                {
                                    return label;
                                }

                                @Override
                                @OnThread(Tag.FX)
                                public void dispose()
                                {
                                }
                            });
                            Bounds bounds = link.localToScreen(link.getBoundsInLocal());
                            ctrl.setAnchorLocation(AnchorLocation.CONTENT_BOTTOM_LEFT);
                            ctrl.show(link, bounds.getMinX(), bounds.getMinY());
                        }
                        else if (!hovering && info != null)
                        {
                            info.hide();
                            info = null;
                        }
                    }
                });
                r.add(link);
            remaining = remaining.substring(endSubst + 1);
        }
        r.add(new Text(remaining + "\n"));
        for (Node node : r)
        {
            node.getStyleClass().add(styleclass);
        }
        return r;
    }

    /**
     * Like getString, but lets the substitutions be dynamic.  If they change,
     * the returned binding will be updated.
     */
    @SuppressWarnings("i18n") // Because checker doesn't recognise what we're doing
    @OnThread(Tag.FXPlatform)
    public static @Localized StringBinding bindString(@LocalizableKey String key, ObservableStringValue firstValue, ObservableStringValue... moreValues)
    {
        List<ObservableStringValue> values = new ArrayList<>();
        values.add(firstValue);
        values.addAll(Arrays.asList(moreValues));
        // This gets it without substitution:
        String unsub = getString(key);
        FXPlatformSupplier<String> update = () -> {
            String sub = unsub;
            for (int i = 0; i < values.size(); i++)
            {
                sub = sub.replace("$" + (i+1), values.get(i).get());
            }
            return sub;
        };
        return Bindings.createStringBinding(update::get, values.<javafx.beans.Observable>toArray(new javafx.beans.Observable[0]));
    }
}
