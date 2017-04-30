package utility.gui;

import annotation.help.qual.HelpKey;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.text.FontSmoothingType;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.scene.web.WebView;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import org.controlsfx.control.PopOver;
import org.controlsfx.control.PopOver.ArrowLocation;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformRunnable;
import utility.Pair;

import java.net.URL;

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
        return new HBox(label(labelKey), helpBox(helpId), choiceNode);
    }

    private static Node helpBox(@HelpKey String helpId)
    {
        return new HelpBox(helpId);
    }

    // Wraps the item in a Pane with the given style classes (on the pane, not content)
    public static Pane wrap(Node content, String... styleClasses)
    {
        Pane p = new BorderPane(content);
        p.getStyleClass().addAll(styleClasses);
        return p;
    }

    private static class HelpBox extends StackPane
    {
        private @Nullable PopOver popOver;

        @SuppressWarnings("initialization") // For the call of showHidePopOver
        public HelpBox(@HelpKey String helpId)
        {
            getStyleClass().add("help-box");
            Circle circle = new Circle(10.0);
            circle.getStyleClass().add("circle");
            Text text = new Text("?");
            text.getStyleClass().add("question");
            getChildren().setAll(circle, text);

            setOnMouseClicked(e -> showHidePopOver(helpId));
        }

        @OnThread(Tag.FXPlatform)
        private void showHidePopOver(@HelpKey String helpId)
        {
            if (popOver != null && popOver.isShowing())
            {
                popOver.hide();
            }
            else
            {
                if (popOver == null)
                {
                    WebView webView = new WebView();
                    webView.setPrefWidth(400.0);
                    webView.setMaxHeight(200.0);
                    URL url = GUI.class.getResource("/" + helpId.replaceAll("/", "-") + ".html");
                    if (url != null)
                    {
                        webView.getEngine().load(url.toString());
                        popOver = new PopOver(wrap(webView, "help-web-wrapper"));
                        popOver.setArrowLocation(ArrowLocation.TOP_LEFT);
                        popOver.setHeaderAlwaysVisible(true);
                        popOver.getStyleClass().add("help-popup");

                        //org.scenicview.ScenicView.show(popOver.getRoot().getScene());
                    }
                }
                // Not guaranteed to have been created, if we can't find the hint:
                if (popOver != null)
                {
                    popOver.show(this);
                }
            }
        }
    }
}
