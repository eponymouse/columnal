package utility.gui;

import annotation.help.qual.HelpKey;
import annotation.qual.Value;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ObservableList;
import javafx.css.Styleable;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Callback;
import javafx.util.StringConverter;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.controlsfx.control.SegmentedButton;
import org.fxmisc.wellbehaved.event.EventPattern;
import org.fxmisc.wellbehaved.event.InputMap;
import org.fxmisc.wellbehaved.event.Nodes;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformFunction;
import utility.FXPlatformRunnable;
import utility.FXPlatformSupplier;
import utility.Pair;
import utility.Utility;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by neil on 17/04/2017.
 */
@OnThread(Tag.FXPlatform)
public class GUI
{
    private static @MonotonicNonNull Image rightClickImage = null;

    public static Button button(@LocalizableKey String msgKey, FXPlatformRunnable onAction, String... styleClasses)
    {
        Button button = new Button(TranslationUtility.getString(msgKey));
        button.setOnAction(e -> onAction.run());
        addIdClass(button, msgKey);
        button.getStyleClass().addAll(styleClasses);
        return button;
    }

    /**
     * Makes a button which, when pressed, shows a context menu immediately beneath it.  If the menu is
     * already showing, hides it instead.
     */
    public static Button buttonMenu(@LocalizableKey String msgKey, FXPlatformSupplier<ContextMenu> makeMenu, String... styleClasses)
    {
        Button button = new Button(TranslationUtility.getString(msgKey));
        SimpleObjectProperty<@Nullable ContextMenu> currentlyShowing = new SimpleObjectProperty<>(null);
        button.setOnAction(e -> {
            @Nullable ContextMenu current = currentlyShowing.get();
            if (current == null)
            {
                current = makeMenu.get();
                currentlyShowing.set(current);
                current.setOnHidden(ev -> {currentlyShowing.set(null);});
                current.show(button, Side.BOTTOM, 0, 0);
            }
            else
            {
                current.hide();
                // Action listener should have done this anyway, but just in case:
                currentlyShowing.set(null);
            }
        });
        addIdClass(button, msgKey);
        button.getStyleClass().addAll(styleClasses);
        return button;
    }

    // Used by TestFX to identify items in the GUI
    public static <T extends Styleable> T addIdClass(T node, @LocalizableKey String msgKey)
    {
        node.getStyleClass().add(makeId(msgKey));
        return node;
    }

    @NonNull
    private static String makeId(@LocalizableKey String msgKey)
    {
        return "id-" + msgKey.replace(".", "-");
    }

    /**
     * Looks up key and makes label with it.
     */
    public static Label label(@LocalizableKey String msgKey, String... styleClasses)
    {
        @Localized String text = TranslationUtility.getString(msgKey);
        return labelRaw(text, styleClasses);
    }

    /**
     * Makes label with exact given text
     */
    public static Label labelRaw(@Localized String text, String... styleClasses)
    {
        Label label = new Label(text);
        label.getStyleClass().addAll(styleClasses);
        return label;
    }

    public static Menu menu(@LocalizableKey String menuNameKey, MenuItem... menuItems)
    {
        Menu menu = new Menu(TranslationUtility.getString(menuNameKey), null, menuItems);
        menu.setId(makeId(menuNameKey));
        return addIdClass(menu, menuNameKey);
    }

    public static MenuItem menuItem(@LocalizableKey String menuItemKey, FXPlatformRunnable onAction)
    {
        @OnThread(Tag.FXPlatform) Pair<@Localized String, @Nullable KeyCombination> stringAndShortcut = TranslationUtility.getStringAndShortcut(menuItemKey);
        MenuItem item = new MenuItem(stringAndShortcut.getFirst());
        item.setOnAction(e -> onAction.run());
        if (stringAndShortcut.getSecond() != null)
            item.setAccelerator(stringAndShortcut.getSecond());
        item.setId(makeId(menuItemKey));
        addIdClass(item, menuItemKey);
        return item;
    }

    public static VBox vbox(String styleClass, Node... contents)
    {
        VBox vBox = new VBox(contents);
        vBox.getStyleClass().add(styleClass);
        return vBox;
    }

    public static HBox hbox(String styleClass, Node... contents)
    {
        HBox hBox = new HBox(contents);
        hBox.getStyleClass().add(styleClass);
        return hBox;
    }

    /**
     * Makes a BorderPane with given top and center, other items left null.
     */
    public static BorderPane borderTopCenter(Node top, Node center, String... styleClasses)
    {
        BorderPane borderPane = new BorderPane(center, top, null, null, null);
        borderPane.getStyleClass().addAll(styleClasses);
        return borderPane;
    }

    /**
     * Makes a BorderPane with given left and right, other items left null.
     */
    public static BorderPane borderLeftRight(Node left, Node right, String... styleClasses)
    {
        BorderPane borderPane = new BorderPane(null, null, right, null, left);
        borderPane.getStyleClass().addAll(styleClasses);
        return borderPane;
    }

    /**
     * Like label but the text is permitted to wrap (by using a TextFlow)
     */
    public static Label labelWrap(@LocalizableKey String contentKey, String... styleClasses)
    {
        Label label = new Label(TranslationUtility.getString(contentKey));
        label.setWrapText(true);
        label.getStyleClass().addAll(styleClasses);
        return label;
    }

    public static Node labelWrapParam(@LocalizableKey String contentKey, String... params)
    {
        Label label = new Label(TranslationUtility.getString(contentKey, params));
        label.setWrapText(true);
        return label;
    }

    public static Node labelled(@LocalizableKey String labelKey, @HelpKey String helpId, Node choiceNode)
    {
        HBox hBox = new HBox(label(labelKey), helpBox(helpId, choiceNode), choiceNode);
        hBox.getStyleClass().add("labelled-wrapper");
        return hBox;
    }

    public static Node labelled(@LocalizableKey String labelKey, Node choiceNode, String... styleClasses)
    {
        Label label = label(labelKey);
        HBox pane = new HBox(label, choiceNode);
        pane.getStyleClass().add("labelled-wrapper");
        pane.getStyleClass().addAll(styleClasses);
        HBox.setHgrow(choiceNode, Priority.ALWAYS);
        return pane;
    }


    public static Node labelledAbove(@LocalizableKey String labelKey, Node item)
    {
        BorderPane borderPane = new BorderPane(item, label(labelKey), null, null, null);
        borderPane.getStyleClass().add("labelled-wrapper-vertical");
        return borderPane;
    }

    public static LabelledGrid.Row labelledGridRow(@LocalizableKey String labelKey, @HelpKey String helpId, Node choiceNode)
    {
        return new LabelledGrid.Row(label(labelKey), helpBox(helpId, choiceNode), choiceNode);
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

    /**
     * If toString() on any item has angle brackets it around, that item gets
     * formatted as italics without the angle brackets.
     */
    public static <C> ComboBox<C> comboBoxStyled(ObservableList<C> cs)
    {
        ComboBox<C> comboBox = new ComboBox<>(cs);
        FXPlatformSupplier<ListCell<C>> cellFactory = () -> {
            return new ListCell<C>() {
                @Override
                @OnThread(Tag.FX)
                public void updateItem(@Nullable C item, boolean empty)
                {
                    super.updateItem(item, empty);
                    if (empty || item == null)
                    {
                        setText("");
                    }
                    else
                    {
                        // run item through StringConverter if it isn't null
                        StringConverter<C> c = comboBox.getConverter();
                        String s = item == null ? comboBox.getPromptText() : (c == null ? item.toString() : c.toString(item));
                        if (s.startsWith("<") && s.endsWith(">"))
                        {
                            setText(s.substring(1, s.length() - 1));
                            setStyle("-fx-font-style: italic;");
                        }
                        else
                        {
                            setText(s);
                            setStyle("");
                        }
                    }
                }
            };
        };
        // From the ComboBox docs:
        comboBox.setButtonCell(cellFactory.get());
        comboBox.setCellFactory(lv -> cellFactory.get());
        return comboBox;
    }

    public static RadioButton radioButton(ToggleGroup toggleGroup, @LocalizableKey String labelKey, String... styleClasses)
    {
        RadioButton item = new RadioButton(TranslationUtility.getString(labelKey));
        item.setToggleGroup(toggleGroup);
        item.getStyleClass().addAll(styleClasses);
        return item;
    }
    
    public static RadioMenuItem radioMenuItem(@LocalizableKey String labelKey, FXPlatformRunnable runWhenSelected)
    {
        RadioMenuItem item = new RadioMenuItem(TranslationUtility.getString(labelKey));
        item.setOnAction(e -> {
            System.err.println("onAction for " + labelKey + " sel: " + item.isSelected());
            runWhenSelected.run();
        });
        return item;
    }

    public static MenuItem[] radioMenuItems(ToggleGroup toggleGroup, RadioMenuItem... radioMenuItems)
    {
        for (RadioMenuItem radioMenuItem : radioMenuItems)
        {
            radioMenuItem.setToggleGroup(toggleGroup);
        }
        return radioMenuItems;
    }

    public static <ROW, VALUE> TableColumn<ROW, VALUE> tableColumn(String columnTitle, FXPlatformFunction<ROW, VALUE> getValue)
    {
        TableColumn<ROW, VALUE> column = new TableColumn<>(columnTitle);
        column.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(getValue.apply(d.getValue())));
        return column;
    }

    public static StackPane withRightClickHint(Node node, Pos position)
    {
        StackPane stackPane;
        if (node instanceof StackPane)
            stackPane = (StackPane) node;
        else
            stackPane = new StackPane(node);

        if (rightClickImage == null)
        {
            ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();
            if (systemClassLoader != null)
            {
                URL imageURL = systemClassLoader.getResource("right-click.png");
                if (imageURL != null)
                {
                    // Oddly enough, one of the tests fails when run from IntelliJ due to a deadlock around loading the image
                    // So we load in background to avoid this:
                    rightClickImage = new Image(imageURL.toExternalForm(), true);
                }
            }
        }

        if (rightClickImage != null)
        {
            ImageView imageView = new ImageView(rightClickImage);
            // Image is mostly transparent, so pick just on our overall bounds:
            imageView.setPickOnBounds(true);
            imageView.setVisible(false);
            StackPane.setAlignment(imageView, position);
            Tooltip.install(imageView, new Tooltip("Right-click menu available"));
            stackPane.getChildren().add(imageView);
            stackPane.addEventFilter(MouseEvent.MOUSE_ENTERED, e -> imageView.setVisible(true));
            stackPane.addEventFilter(MouseEvent.MOUSE_EXITED, e -> imageView.setVisible(false));
        }
        return stackPane;
    }

    /**
     * Whenever this label is abbreviated (i.e. has an ellipsis that hides some text),
     * add a tooltip
     * @param label
     */
    public static void showTooltipWhenAbbrev(Label label)
    {
        Tooltip tooltip = new Tooltip(label.getText());
        FXPlatformRunnable update = () -> {
            if (label.prefWidth(label.getHeight()) > label.getWidth())
            {
                Tooltip.install(label, tooltip);
            }
            else
            {
                Tooltip.uninstall(label, tooltip);
            }
        };
        FXUtility.addChangeListenerPlatformNN(label.prefWidthProperty(), w -> update.run());
        FXUtility.addChangeListenerPlatformNN(label.widthProperty(), w -> update.run());
        FXUtility.addChangeListenerPlatformNN(label.textProperty(), w -> update.run());
    }
}
