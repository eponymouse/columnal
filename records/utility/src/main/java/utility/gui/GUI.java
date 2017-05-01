package utility.gui;

import annotation.help.qual.HelpKey;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.controlsfx.control.SegmentedButton;
import org.fxmisc.wellbehaved.event.EventPattern;
import org.fxmisc.wellbehaved.event.InputMap;
import org.fxmisc.wellbehaved.event.Nodes;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformRunnable;
import utility.Pair;
import utility.Utility;

import java.util.ArrayList;
import java.util.List;

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

    public static VBox vbox(String styleClass, Node... contents)
    {
        VBox vBox = new VBox(contents);
        vBox.getStyleClass().add(styleClass);
        return vBox;
    }

    /**
     * Like label but the text is permitted to wrap (by using a TextFlow)
     */
    public static Node labelWrap(@LocalizableKey String contentKey, String... styleClasses)
    {
        TextFlow textFlow = new TextFlow(new Text(TranslationUtility.getString(contentKey)));
        textFlow.getStyleClass().addAll(styleClasses);
        return textFlow;
    }

    public static Node labelled(@LocalizableKey String labelKey, @HelpKey String helpId, Node choiceNode)
    {
        return new HBox(label(labelKey), helpBox(helpId, choiceNode), choiceNode);
    }

    private static HelpBox helpBox(@HelpKey String helpId, @Nullable Node relevantNode)
    {
        List<Node> nodes = new ArrayList<>();
        if (relevantNode != null)
        {
            if (relevantNode.isFocusTraversable())
            {
                nodes.add(relevantNode);
            }
            else if (relevantNode instanceof SegmentedButton)
            {
                nodes.addAll(((SegmentedButton)relevantNode).getButtons());
            }
        }
        HelpBox helpBox = new HelpBox(helpId);
        helpBox.bindKeyboardFocused(Bindings.createBooleanBinding(() -> {
            return nodes.stream().map(Node::isFocused).reduce(false, (a, b) -> a || b);
        }, Utility.mapList(nodes, Node::focusedProperty).toArray(new Observable[0])));
        for (Node n : nodes)
        {
            Nodes.addInputMap(n, InputMap.consume(EventPattern.keyPressed(KeyCode.F1), e -> helpBox.cycleStates()));
        }
        return helpBox;
    }

    // Wraps the item in a Pane with the given style classes (on the pane, not content)
    public static Pane wrap(Node content, String... styleClasses)
    {
        Pane p = new BorderPane(content);
        p.getStyleClass().addAll(styleClasses);
        return p;
    }

}
