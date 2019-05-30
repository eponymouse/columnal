package utility.gui;

import com.google.common.collect.ImmutableList;
import com.sun.javafx.tk.TKPulseListener;
import com.sun.javafx.tk.Toolkit;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanExpression;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.binding.DoubleExpression;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableBooleanValue;
import javafx.beans.value.ObservableObjectValue;
import javafx.beans.value.ObservableStringValue;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.event.EventHandler;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Dimension2D;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.geometry.VerticalDirection;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.cell.TextFieldListCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.DataFormat;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCharacterCombination;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyCombination.Modifier;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Region;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Modality;
import javafx.stage.PopupWindow.AnchorLocation;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import javafx.util.StringConverter;
import log.ErrorHandler.RunOrError;
import log.Log;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.resizers.configurations.Antialiasing;
import net.coobird.thumbnailator.resizers.configurations.Rendering;
import net.coobird.thumbnailator.resizers.configurations.ScalingMode;
import org.apache.commons.lang3.SystemUtils;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.KeyFor;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import org.controlsfx.validation.ValidationResult;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.AcceleratorBaseVisitor;
import records.grammar.AcceleratorLexer;
import records.grammar.AcceleratorParser;
import records.grammar.AcceleratorParser.AcceleratorContext;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by neil on 17/02/2017.
 */
@OnThread(Tag.FXPlatform)
public class FXUtility
{
    private static boolean testingMode = false;
    private static final Set<String> loadedFonts = new HashSet<>();
    // Need to keep strong reference until they run:
    private static ArrayList<TKPulseListener> pulseListeners = new ArrayList<>();

    @OnThread(Tag.FXPlatform)
    public static <T> ListView<@NonNull T> readOnlyListView(ObservableList<@NonNull T> content, Function<T, String> toString)
    {
        ListView<@NonNull T> listView = new ListView<>(content);
        listView.setCellFactory((ListView<@NonNull T> lv) -> {
            return new TextFieldListCell<@NonNull T>(new StringConverter<@NonNull T>()
            {
                @Override
                public String toString(T t)
                {
                    return toString.apply(t);
                }

                @Override
                public @NonNull T fromString(String string)
                {
                    throw new UnsupportedOperationException();
                }
            });
        });
        listView.setEditable(false);
        return listView;
    }

    public static <T> void enableDragFrom(ListView<T> listView, String type, TransferMode transferMode)
    {
        enableDragFrom(listView, type, transferMode, x -> x, null);
    }

    public static <T, U> void enableDragFrom(ListView<T> listView, String type, TransferMode transferMode, FXPlatformFunction<List<T>, U> mapToSerializable, @Nullable FXPlatformBiConsumer<@Nullable TransferMode, U> onDragComplete)
    {
        DataFormat textDataFormat = getTextDataFormat(type);
        listView.setOnDragDetected(e -> {
            Dragboard db = listView.startDragAndDrop(transferMode);
            List<T> selected = new ArrayList<>(listView.getSelectionModel().getSelectedItems());
            db.setContent(Collections.singletonMap(textDataFormat, mapToSerializable.apply(selected)));
            e.consume();
        });
        listView.setOnDragDone(e -> {
            Object content = e.getDragboard().getContent(textDataFormat);
            if (onDragComplete != null)
            {
                try
                {
                    @SuppressWarnings("unchecked")
                    U contentCast = (U) content;
                    onDragComplete.consume(e.getTransferMode(), contentCast);
                }
                catch (ClassCastException ex)
                {
                }
                e.consume();
            }
        });
    }

    public static @NonNull DataFormat getTextDataFormat(String subType)
    {
        String whole = "text/" + subType;
        return getDataFormat(whole);
    }

    /**
     * Lookups or creates the given DataFormat.  It is important to
     * use this rather than the constructor directly,
     *  to prevent exception which occurs on duplicate
     *  creation.
     */
    public static DataFormat getDataFormat(String whole)
    {
        DataFormat f = DataFormat.lookupMimeType(whole);
        if (f != null)
            return f;
        else
            return new DataFormat(whole);
    }

    @OnThread(Tag.FX)
    public static void sizeToFit(TextField tf, @Nullable Double minSizeFocused, @Nullable Double minSizeUnfocused, @Nullable DoubleExpression maxSize)
    {
        // Partly taken from http://stackoverflow.com/a/25643696/412908:
        // Set Max and Min Width to PREF_SIZE so that the TextField is always PREF
        tf.setMinWidth(Region.USE_PREF_SIZE);
        tf.setMaxWidth(Region.USE_PREF_SIZE);
        tf.prefWidthProperty().bind(new DoubleBinding()
        {
            {
                super.bind(tf.textProperty());
                super.bind(tf.promptTextProperty());
                super.bind(tf.fontProperty());
                super.bind(tf.focusedProperty());
                if (maxSize != null)
                    super.bind(maxSize);
            }
            @Override
            @OnThread(Tag.FX)
            protected double computeValue()
            {
                Text text = new Text(tf.getText());
                if (text.getText().isEmpty() && !tf.getPromptText().isEmpty())
                    text.setText(tf.getPromptText());
                text.setFont(tf.getFont()); // Set the same font, so the size is the same
                double width = text.getLayoutBounds().getWidth() // This big is the Text in the TextField
                    //+ tf.getPadding().getLeft() + tf.getPadding().getRight() // Add the padding of the TextField
                    + tf.getInsets().getLeft() + tf.getInsets().getRight()
                    + 1d; // Add some spacing
                return Math.min(Math.max(tf.isFocused() ? (minSizeFocused == null ? 20 : minSizeFocused) : (minSizeUnfocused == null ? 20 : minSizeUnfocused), width), maxSize == null ? Double.MAX_VALUE : maxSize.get());
            }
        });
    }

    @OnThread(Tag.FX)
    public static ValidationResult validate(Control target, ExRunnable action)
    {
        try
        {
            action.run();
            return new ValidationResult();
        }
        catch (InternalException e)
        {
            return ValidationResult.fromError(target, "Internal Error: " + e.getLocalizedMessage());
        }
        catch (UserException e)
        {
            return ValidationResult.fromError(target, e.getLocalizedMessage());
        }
    }

    /**
     * Gets a list of stylesheets to add to a Scene.  Automatically adds
     * general.css if not present.  Each name can either be a full filename (e.g. initial.css)
     * or a stem (e.g. initial); the .css extension is added automatically if not present.
     *
     * @param stylesheetNames
     * @return A list ready to pass to Scene#getStylesheets().addAll()
     */
    public static List<String> getSceneStylesheets(String... stylesheetNames)
    {
        return Stream.<@NonNull String>concat(
                   Stream.<String>of("general.css", "stableview.css"),
                   Arrays.stream(stylesheetNames).<String>map(s -> s.endsWith(".css") ? s : (s + ".css"))
                 )
                 .distinct()
                 .map(FXUtility::getStylesheet)
                 .collect(Collectors.<String>toList());
    }

    public static ExtensionFilter getProjectExtensionFilter(String extensionInclDot)
    {
        return new ExtensionFilter(TranslationUtility.getString("extension.projects"), "*" + extensionInclDot);
    }

    @OnThread(Tag.Any)
    public static String getStylesheet(String stylesheetName)
    {
        try
        {
            ClassLoader classLoader = ClassLoader.getSystemClassLoader();
            if (classLoader == null)
                return "";
            URL resource = classLoader.getResource(stylesheetName);
            if (resource == null)
                return "";
            return resource.toString();
        }
        catch (NullPointerException e)
        {
            Log.log("Problem loading stylesheet: " + stylesheetName, e);
            return "";
        }
    }

    @SuppressWarnings("nullness")
    public static void ensureFontLoaded(String fontFileName)
    {
        if (!loadedFonts.contains(fontFileName))
        {
            try (@Nullable InputStream fis = ClassLoader.getSystemClassLoader().getResourceAsStream(fontFileName))
            {
                if (fis == null)
                {
                    Log.logStackTrace("Cannot load font as file not found: \"" + fontFileName + "\"");
                }
                else
                {
                    if (Font.loadFont(fis, 10) == null)
                    {
                        Log.logStackTrace("Cannot load font for unknown reason: \"" + fontFileName + "\"");
                    }
                    else
                    {
                        loadedFonts.add(fontFileName);
                    }
                }
            }
            catch (IOException | NullPointerException e)
            {
                Log.log(e);
            }
        }
    }

    @OnThread(Tag.FXPlatform)
    @SuppressWarnings("nullness")
    public static <T> void addChangeListenerPlatform(ObservableValue<T> property, FXPlatformConsumer<? super @Nullable T> listener)
    {
        // Defeat thread checker:
        property.addListener(new ChangeListener<T>()
        {
            @Override
            @OnThread(value = Tag.FXPlatform, ignoreParent = true)
            public void changed(ObservableValue<? extends T> a, T b, T newVal)
            {
                listener.consume(newVal);
            }
        });
    }

    @OnThread(Tag.FXPlatform)
    public static <T> void addChangeListenerPlatformAndCallNow(ObservableValue<T> property, FXPlatformConsumer<? super @Nullable T> listener)
    {
        addChangeListenerPlatform(property, listener);
        listener.consume(property.getValue());
    }

    @OnThread(Tag.FXPlatform)
    public static <T> void addChangeListenerPlatformNNAndCallNow(ObservableValue<@NonNull T> property, FXPlatformConsumer<@NonNull T> listener)
    {
        addChangeListenerPlatformNN(property, listener);
        listener.consume(property.getValue());
    }

    @OnThread(Tag.FXPlatform)
    @SuppressWarnings("nullness")
    // NN = Not Null
    public static <T> void addChangeListenerPlatformNN(ObservableValue<@NonNull T> property, FXPlatformConsumer<@NonNull ? super T> listener)
    {
        // Defeat thread checker:
        property.addListener(new ChangeListener<T>()
        {
            @Override
            @OnThread(value = Tag.FXPlatform, ignoreParent = true)
            public void changed(ObservableValue<? extends T> a, T b, T newVal)
            {
                listener.consume(newVal);
            }
        });
    }

    @OnThread(Tag.FXPlatform)
    public static void runAfter(FXPlatformRunnable r)
    {
        // Defeat thread-checker:
        ((Runnable)(() -> Platform.runLater(r::run))).run();
    }

    /**
     * Runs the given action after the given delay, unless you call the returned cancellation
     * action in the mean-time.  Calling it later on has no effect.
     */
    public static FXPlatformRunnable runAfterDelay(Duration duration, FXPlatformRunnable action)
    {
        Timeline t = new Timeline(new KeyFrame(duration, e -> action.run()));
        t.play();
        return t::stop;
    }
    
    // Like Platform.runLater from Simulation thread, but also stores caller for logging.
    @OnThread(Tag.Simulation)
    public static void runFX(FXPlatformRunnable runnable)
    {
        ImmutableList<StackTraceElement[]> callerStack = Log.getTotalStack();
        Platform.runLater(() -> {
            Log.storeThreadedCaller(callerStack);
            runnable.run();
            // Blank stack afterwards, to avoid confusion:
            Log.storeThreadedCaller(null);
        });
    }

    // Mainly, this method is to avoid having to cast to ListChangeListener to disambiguate
    // from the invalidionlistener overload in ObservableList
    @OnThread(Tag.FXPlatform)
    public static <T> void listen(ObservableList<T> list, FXPlatformConsumer<ListChangeListener.Change<? extends T>> listener)
    {
        list.addListener((ListChangeListener<T>)(ListChangeListener.Change<? extends T> c) -> listener.consume(c));
    }

    @OnThread(Tag.FXPlatform)
    public static <T> void listenAndCallNow(ObservableList<T> list, FXPlatformConsumer<List<T>> listener)
    {
        list.addListener((ListChangeListener<T>)(ListChangeListener.Change<? extends T> c) -> listener.consume(list));
        listener.consume(list);
    }

    public static void forcePrefSize(@UnknownInitialization(Region.class) Region region)
    {
        region.setMinWidth(Region.USE_PREF_SIZE);
        region.setMaxWidth(Region.USE_PREF_SIZE);
        region.setMinHeight(Region.USE_PREF_SIZE);
        region.setMaxHeight(Region.USE_PREF_SIZE);
    }

    public static void onFocusLostOnce(@NonNull Node node, FXPlatformRunnable onLost)
    {
        node.focusedProperty().addListener(new ChangeListener<Boolean>()
        {
            @Override
            @OnThread(value = Tag.FXPlatform,ignoreParent = true)
            public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue)
            {
                if (!newValue)
                {
                    node.focusedProperty().removeListener(this);
                    onLost.run();
                }
            }
        });
    }

    public static double measureNotoSansHeight()
    {
        Text t = new Text("TyqX"); // Should be full height
        t.setFont(new Font("Noto Sans Display", 13));
        return t.getLayoutBounds().getHeight();
    }

    /**
     * Used to "wash" an in-construction item when setting a mouse handler,
     * to avoid invalid initialization warnings -- the handler won't be executed
     * until after the constructor because it requires user mouse action,
     * so it's safe to assume the item is initialized.
     */
    @SuppressWarnings("initialization")
    @OnThread(Tag.FX)
    @Pure
    public static <T> T mouse(@UnknownInitialization T item)
    {
        return item;
    }

    // As mouse method above, but for when we are doing a key listener
    @SuppressWarnings("initialization")
    @Pure
    public static <T> T keyboard(@UnknownInitialization T item)
    {
        return item;
    }

    // As mouse method above, but for when we are doing a focus listener
    @SuppressWarnings("initialization")
    @Pure
    public static <T> T focused(@UnknownInitialization T item)
    {
        return item;
    }

    public static void _test_setTestingMode()
    {
        testingMode = true;
    }

    public static @Nullable File getFileSaveAs(Node parent)
    {
        Window parentWindow = parent.getScene() == null ? null : parent.getScene().getWindow();
        
        if (testingMode)
        {
            TextInputDialog textInputDialog = new TextInputDialog();
            if (parentWindow != null)
                textInputDialog.initOwner(parentWindow);
            return textInputDialog.showAndWait().map(File::new).orElse(null);
        }
        else
        {
            return new FileChooser().showSaveDialog(parentWindow);
        }
    }


    private static List<Exception> queuedErrors = new ArrayList<>();
    private static boolean showingError = false;

    // From https://stackoverflow.com/a/12717377/412908 but tweaked to check all threads
    private static boolean isJUnitTest()
    {
        // Need to defeat thread checker because getAllStackTraces
        // is currently annotated wrongly:
        for (StackTraceElement[] stackTrace : ((Supplier<Map<Thread, StackTraceElement[]>>)Thread::getAllStackTraces).get().values())
        {
            List<StackTraceElement> list = Arrays.asList(stackTrace);
            for (StackTraceElement element : list)
            {
                if (element.getClassName().startsWith("org.junit."))
                {
                    return true;
                }
            }
        }
        return false;
    }


    @OnThread(Tag.Simulation)
    public static void alertOnError_(String title, RunOrError r)
    {
        alertOnError_(title, err -> err, r);
    }

    @OnThread(Tag.Simulation)
    public static void alertOnError_(String title, Function<@Localized String, @Localized String> errWrap, RunOrError r)
    {
        try
        {
            r.run();
        }
        catch (InternalException | UserException e)
        {
            Platform.runLater(() ->
            {
                showError(title, errWrap, e);
            });
        }
    }

    @OnThread(Tag.FXPlatform)
    public static void alertOnErrorFX_(String title, RunOrErrorFX r)
    {
        try
        {
            r.run();
        }
        catch (InternalException | UserException e)
        {
            showError(title, e);
        }
    }

    @OnThread(Tag.FXPlatform)
    public static <T> @Nullable T alertOnErrorFX(String title, GenOrErrorFX<T> r)
    {
        try
        {
            return r.run();
        }
        catch (InternalException | UserException e)
        {
            showError(title, e);
            return null;
        }
    }

    @OnThread(Tag.FXPlatform)
    public static void showError(String title, Exception e)
    {
        showError(title, x -> x, e);
    }

    @OnThread(Tag.FXPlatform)
    public static void showError(String title, Function<@Localized String, @Localized String> errWrap, Exception e)
    {
        if (showingError)
        {
            // TODO do something with the queued errors; add to shown dialog?
            queuedErrors.add(e);
        }
        else
        {
            Log.log("Showing error:", e);
            // Don't show dialog which would interrupt a JUnit test:
            if (!isJUnitTest())
            {
                String localizedMessage = errWrap.apply(e.getLocalizedMessage());
                localizedMessage = localizedMessage == null ? "Unknown error" : localizedMessage;
                Alert alert = new Alert(AlertType.ERROR, localizedMessage, ButtonType.OK);
                alert.getDialogPane().getStylesheets().addAll(
                    FXUtility.getStylesheet("general.css"), 
                    FXUtility.getStylesheet("dialogs.css")
                );
                alert.setTitle(title);
                alert.setHeaderText(title);
                alert.initModality(Modality.APPLICATION_MODAL);
                if (e.getCause() != null)
                {
                    TextArea textArea = new TextArea(e.getCause().getLocalizedMessage());
                    textArea.setEditable(false);
                    textArea.getStyleClass().add("loading-error-detail");
                    alert.getDialogPane().setExpandableContent(textArea);
                }
                alert.setResizable(true);
                showingError = true;
                alert.showAndWait();
                showingError = false;
            }
        }
    }

    @OnThread(Tag.Simulation)
    public static <T> Optional<T> alertOnError(String title, GenOrError<@Nullable T> r)
    {
        try
        {
            @Nullable T t = r.run();
            if (t == null)
                return Optional.empty();
            else
                return Optional.of(t);
        }
        catch (InternalException | UserException e)
        {
            Platform.runLater(() ->
            {
                showError(title, e);
            });
            return Optional.empty();
        }
    }

    // From https://stackoverflow.com/questions/12837592/how-to-scroll-to-make-a-node-within-the-content-of-a-scrollpane-visible
    // And made to actually work!
    public static void scrollTo(ScrollPane scrollPane, Node node)
    {
        Bounds viewport = scrollPane.getViewportBounds();
        Bounds scrollBoundsInScene = scrollPane.localToScene(scrollPane.getBoundsInLocal());
        double nodeMinY = node.localToScene(node.getBoundsInLocal()).getMinY();
        double nodeMaxY = node.localToScene(node.getBoundsInLocal()).getMaxY();

        double vValueDelta = 0;
        double vValueCurrent = scrollPane.getVvalue();

        if (nodeMaxY < scrollBoundsInScene.getMinY()) {
            // currently located above (remember, top left is (0,0))
            vValueDelta = (nodeMinY - viewport.getHeight()) / scrollPane.getContent().getBoundsInLocal().getHeight();
        } else if (nodeMinY > scrollBoundsInScene.getMaxY()) {
            // currently located below
            vValueDelta = (nodeMinY + viewport.getHeight()) / scrollPane.getContent().getBoundsInLocal().getHeight();
        }
        scrollPane.setVvalue(vValueCurrent + vValueDelta);
    }

    /**
     * If code is an F key (F1, F2, etc), returns the number as an int
     * (F1 is 1, not zero, F2 is 2, etc).  Otherwise, empty is returned.
     */
    public static OptionalInt FKeyNumber(KeyCode code)
    {
        if (code.compareTo(KeyCode.F1) >= 0 && code.compareTo(KeyCode.F24) <= 0)
        {
            return OptionalInt.of(code.ordinal() - KeyCode.F1.ordinal() + 1);
        }
        else
        {
            return OptionalInt.empty();
        }
    }

    // Like Node's resizeRelocate, but doesn't account for the bounds size.  Places the Node *EXACTLY* where
    // it is requested.
    public static void resizeRelocate(Node node, double x, double y, double width, double height)
    {
        node.setLayoutX(x);
        node.setLayoutY(y);
        node.resize(width, height);
    }

    /**
     * Buttons in JavaFX need to see the press and release actions to fire.  But if there is a popup showing,
     * the first press gets consumed dismissing the popup, even if the press is actually on the button.  So
     * what this function does is make clicking on any button in the DialogPane fire it anyway, even if we
     * didn't see the press.  This allows you to directly click cancel or OK, even if there is an autocomplete
     * showing.  Note: you must call this after you have set the button types in the dialog!
     */
    public static void fixButtonsWhenPopupShowing(DialogPane dialogPane)
    {
        for (ButtonType buttonType : dialogPane.getButtonTypes())
        {
            Button button = (Button) dialogPane.lookupButton(buttonType);
            button.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY)
                {
                    button.fire();
                    e.consume();
                }
            });
        }
    }

    // Ensures selected item is in view.
    public static void ensureSelectionInView(ListView<?> listView, @Nullable VerticalDirection moveDirection)
    {
        FXUtility.runAfter(() -> {
            int selIndex = listView.getSelectionModel().getSelectedIndex();
            if (selIndex < 0)
                return; // No selection

            // Determine if the selection is in view:
            // Can't use pseudo-classes due to https://bugs.java.com/bugdatabase/view_bug.do?bug_id=JDK-8185831
            // so use stream-filter afterwards:
            @Nullable Node topCell = listView.lookup(".list-cell");
            @Nullable Node selectedCell = listView.lookupAll(".list-cell").stream().filter(c -> ((ListCell<?>) c).isSelected()).findFirst().orElse(null);
            Bounds listViewSceneBounds = listView.localToScene(listView.getBoundsInLocal());
            if (selectedCell == null || !listViewSceneBounds.contains(selectedCell.localToScene(new Point2D(0, 10))))
            {
                if (moveDirection == VerticalDirection.UP)
                    listView.scrollTo(selIndex);
                else
                {
                    int pageSize = (int) Math.floor(listViewSceneBounds.getHeight() / (topCell == null ? 24 : topCell.getBoundsInLocal().getHeight()));
                    listView.scrollTo(Math.max(0, selIndex - pageSize + 1));
                }
            }
        });
    }

    public static void preventCloseOnEscape(DialogPane dialogPane)
    {
        // I tried to use setCancelButton(false) but that isn't enough to prevent escape cancelling, so we consume
        // the keypress:
        dialogPane.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE)
                e.consume();
        });
    }

    public static boolean hasPseudoclass(Node node, String className)
    {
        return node.getPseudoClassStates().stream().anyMatch(p -> p.getPseudoClassName().equals(className));
    }

    @OnThread(Tag.Any)
    public static Rectangle2D boundsToRect(Bounds bounds)
    {
        return new Rectangle2D(bounds.getMinX(), bounds.getMinY(), bounds.getWidth(), bounds.getHeight());
    }

    public static Rectangle2D intersectRect(Rectangle2D a, Rectangle2D b)
    {
        double x = Math.max(a.getMinX(), b.getMinX());
        double y = Math.max(a.getMinY(), b.getMinY());
        return new Rectangle2D(
            x, y,
            Math.min(a.getMaxX(), b.getMaxX()) - x,
                Math.min(a.getMaxY(), b.getMaxY()) - y
        );
    }

    @OnThread(Tag.Any)
    public static Point2D getCentre(Rectangle2D rectangle2D)
    {
        return new Point2D(rectangle2D.getMinX() + rectangle2D.getWidth()*0.5, rectangle2D.getMinY() + rectangle2D.getHeight()*0.5);
    }

    // Just uses the bounds limits like a rectangle.
    @OnThread(Tag.Any)
    public static Point2D getCentre(Bounds bounds)
    {
        return new Point2D(bounds.getMinX() + bounds.getWidth()*0.5, bounds.getMinY() + bounds.getHeight()*0.5);
    }

    public static BoundingBox getWindowBounds(Window w)
    {
        return new BoundingBox(w.getX(), w.getY(), w.getWidth(), w.getHeight());
    }

    public static @Nullable Dimension2D sizeOfBiggestScreen(Window parentWindow)
    {
        return Screen.getScreensForRectangle(parentWindow.getX(), parentWindow.getY(), parentWindow.getWidth(), parentWindow.getHeight())
            .stream()
            .map(s -> new Dimension2D(s.getVisualBounds().getWidth(), s.getVisualBounds().getHeight()))
            .sorted(Comparator.comparing(d -> -1.0 * d.getWidth() * d.getHeight()))
            .findFirst().orElse(null);
        
    }

    public static void enableWindowMove(@UnknownInitialization Window window, Node root)
    {
        root.addEventHandler(MouseEvent.ANY, new EventHandler<MouseEvent>()
        {
            private double[] pressedDelta = new double[2];

            @Override
            public void handle(MouseEvent event)
            {
                if (event.getEventType() == MouseEvent.MOUSE_PRESSED)
                {
                    pressedDelta[0] = event.getScreenX() - mouse(window).getX();
                    pressedDelta[1] = event.getScreenY() - mouse(window).getY();
                }
                else if (event.getEventType() == MouseEvent.MOUSE_DRAGGED)
                {
                    mouse(window).setX(event.getScreenX() - pressedDelta[0]);
                    mouse(window).setY(event.getScreenY() - pressedDelta[1]);
                }
            }
        });
    }

    public static void runAfterNextLayout(final FXPlatformRunnable action)
    {
        //TODO In Java 9, use public toolkit
        TKPulseListener pulseListener = new TKPulseListener()
        {
            @Override
            public void pulse()
            {
                runAfter(action);
                Toolkit.getToolkit().removePostSceneTkPulseListener(this);
                pulseListeners.remove(this);
            }
        };
        // Need to keep strong reference, as Toolkit only keeps weak reference so it may get GCed: 
        pulseListeners.add(pulseListener);
        Toolkit.getToolkit().addPostSceneTkPulseListener(pulseListener);
    }

    public static boolean wordSkip(KeyEvent keyEvent)
    {
        return SystemUtils.IS_OS_MAC_OSX ? keyEvent.isAltDown() : keyEvent.isControlDown();
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
    String unsub = TranslationUtility.getString(key);
    FXPlatformSupplier<String> update = () -> {
        String sub = unsub;
        for (int i = 0; i < values.size(); i++)
        {
            sub = sub.replace("$" + (i+1), values.get(i).get());
        }
        return sub;
    };
    return Bindings.createStringBinding(() -> update.get(), values.<javafx.beans.Observable>toArray(new javafx.beans.Observable[0]));
}

    public static @Nullable ImageView makeImageView(String filename, @Nullable Integer maxWidth, @Nullable Integer maxHeight)
    {
        ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();
        if (systemClassLoader != null)
        {
            URL imageURL = systemClassLoader.getResource(filename);
            if (imageURL != null)
            {
                ImageView imageView;
                try
                {
                    File destFile = File.createTempFile("img", ".png");
                    destFile.deleteOnExit();
                    Thumbnails.of(imageURL)
                        .scalingMode(ScalingMode.BICUBIC)
                        .rendering(Rendering.QUALITY)
                        .antialiasing(Antialiasing.ON)
                        .outputQuality(1.0)
                        .size(maxWidth == null ? 1000 : maxWidth.intValue(), maxHeight == null ? 1000 : maxHeight.intValue())
                        .keepAspectRatio(true)
                        .allowOverwrite(true)
                        .toFile(destFile);
                    File destFile2x = new File(destFile.getAbsolutePath().replace(".png", "@2x.png"));
                    destFile2x.deleteOnExit();
                    Thumbnails.of(imageURL)
                        .scalingMode(ScalingMode.BICUBIC)
                        .rendering(Rendering.QUALITY)
                        .antialiasing(Antialiasing.ON)
                        .outputQuality(1.0)
                        .size(maxWidth == null ? 2000 : maxWidth.intValue() * 2, maxHeight == null ? 2000 : maxHeight.intValue() * 2)
                        .keepAspectRatio(true)
                        .allowOverwrite(true)
                        .toFile(destFile);

                    imageView = new ImageView(destFile.toURI().toURL().toExternalForm());
                }
                catch (Exception e)
                {
                    Log.log(e);
                    // Give up resizing:
                    imageView = new ImageView(imageURL.toExternalForm());
                }
                if (maxHeight != null)
                    imageView.setFitHeight(maxHeight);
                if (maxWidth != null)
                    imageView.setFitWidth(maxWidth);
                imageView.setSmooth(true);
                imageView.setPreserveRatio(true);
                return imageView;
            }
        }
        return null;
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
        @Localized String original = TranslationUtility.getString(key);
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

    @SuppressWarnings("i18n") // Because we return original if there's an issue
    @OnThread(Tag.FXPlatform)
    public static Pair<@Localized String, @Nullable KeyCombination> getStringAndShortcut(@LocalizableKey String key)
    {
        String original = TranslationUtility.getString(key);
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
                            if (ctx.KEY().getText().equals("Esc"))
                                return new KeyCodeCombination(KeyCode.ESCAPE, modifiers.toArray(new Modifier[0]));
                            else
                                return new KeyCharacterCombination(ctx.KEY().getText(), modifiers.toArray(new Modifier[0]));
                        }
                    }.visit(p.accelerator());
                });
            }
            catch (InternalException | UserException e)
            {
                // No need to tell the user, it's an internal error:
                Log.log(e);
            }
            return new Pair<>(original.substring(0, atIndex), shortcut);
        }
        else
            return new Pair<>(original, null);
    }

    public static interface DragHandler
    {
        @OnThread(Tag.FXPlatform)
        default void dragExited() {}

        @OnThread(Tag.FXPlatform)
        default void dragMoved(Point2D pointInScene) {};

        @OnThread(Tag.FXPlatform)
        boolean dragEnded(Dragboard dragboard, Point2D pointInScene);
    }

    // Point is in Scene
    public static void enableDragTo(Node destination, Map<DataFormat, DragHandler> receivers)
    {
        destination.setOnDragExited(e -> {
            for (DragHandler dragHandler : receivers.values())
            {
                dragHandler.dragExited();
            }
        });
        destination.setOnDragOver(e -> {
            boolean accepts = false;
            for (Entry<@KeyFor("receivers") DataFormat, DragHandler> receiver : receivers.entrySet())
            {
                if (e.getDragboard().hasContent(receiver.getKey()))
                {
                    accepts = true;
                    receiver.getValue().dragMoved(new Point2D(e.getSceneX(), e.getSceneY()));
                }
            }
            if (accepts)
                e.acceptTransferModes(TransferMode.COPY_OR_MOVE);
            e.consume();
        });
        destination.setOnDragDropped(e -> {
            boolean dropped = false;
            for (Entry<@KeyFor("receivers") DataFormat, DragHandler> receiver : receivers.entrySet())
            {
                if (e.getDragboard().hasContent(receiver.getKey()))
                {
                    dropped = receiver.getValue().dragEnded(e.getDragboard(), new Point2D(e.getSceneX(), e.getSceneY()));
                }
            }
            if (dropped)
                e.setDropCompleted(true);
        });
    }

    public static <T, R> ObjectBinding<R> mapBindingLazy(ObservableObjectValue<T> original, FXPlatformFunction<T, R> extract)
    {
        return Bindings.createObjectBinding(() -> extract.apply(original.get()), original);
    }

    public static <T, R> ObjectExpression<R> mapBindingEager(ObservableValue<@Nullable T> original, FXPlatformFunction<@Nullable T, R> extract)
    {
        return mapBindingEager(original, extract, ImmutableList.of());
    }

    public static <T, R> ObjectExpression<R> mapBindingEager(ObservableValue<@Nullable T> original, FXPlatformFunction<@Nullable T, R> extract, ImmutableList<ObservableValue<?>> otherDependencies)
    {
        ObjectProperty<R> binding = new SimpleObjectProperty<>(extract.apply(original.getValue()));
        addChangeListenerPlatform(original, x -> binding.setValue(extract.apply(x)));
        for (ObservableValue<?> otherDep : otherDependencies)
        {
            addChangeListenerPlatform(otherDep, y -> binding.setValue(extract.apply(original.getValue())));
        }
        return binding;
    }

    public static <T, R> ObjectExpression<@NonNull R> mapBindingEagerNN(ObservableValue<@NonNull T> original, FXPlatformFunction<@NonNull T, @NonNull R> extract)
    {
        ObjectProperty<@NonNull R> binding = new SimpleObjectProperty<>(extract.apply(original.getValue()));
        addChangeListenerPlatformNN(original, x -> binding.setValue(extract.apply(x)));
        return binding;
    }

    @OnThread(Tag.FX)
    public static void setPseudoclass(@UnknownInitialization(Node.class) Node node, String className, boolean on)
    {
        node.pseudoClassStateChanged(PseudoClass.getPseudoClass(className), on);
    }

    @OnThread(Tag.FXPlatform)
    public static void bindPseudoclass(Node node, String className, BooleanExpression onExpression)
    {
        addChangeListenerPlatformNN(onExpression, b -> setPseudoclass(node, className, b));
    }

    // What's the shortest distance from the point to the left-hand side of the node?
    public static double distanceToLeft(Node node, Point2D pointInScene)
    {
        Bounds boundsInScene = node.localToScene(node.getBoundsInLocal());
        if (pointInScene.getY() < boundsInScene.getMinY())
        {
            return Math.hypot(pointInScene.getX() - boundsInScene.getMinX(), pointInScene.getY() - boundsInScene.getMinY());
        }
        else if (pointInScene.getY() > boundsInScene.getMaxY())
        {
            return Math.hypot(pointInScene.getX() - boundsInScene.getMinX(), pointInScene.getY() - boundsInScene.getMaxY());
        }
        else
        {
            // On same vertical level as edge, so just use difference:
            return Math.abs(pointInScene.getX() - boundsInScene.getMinX());
        }
    }

    // What's the shortest distance from the point to the left-hand side of the node?
    public static double distanceToRight(Node node, Point2D pointInScene)
    {
        Bounds boundsInScene = node.localToScene(node.getBoundsInLocal());
        if (pointInScene.getY() < boundsInScene.getMinY())
        {
            return Math.hypot(pointInScene.getX() - boundsInScene.getMaxX(), pointInScene.getY() - boundsInScene.getMinY());
        }
        else if (pointInScene.getY() > boundsInScene.getMaxY())
        {
            return Math.hypot(pointInScene.getX() - boundsInScene.getMaxX(), pointInScene.getY() - boundsInScene.getMaxY());
        }
        else
        {
            // On same vertical level as edge, so just use difference:
            return Math.abs(pointInScene.getX() - boundsInScene.getMaxX());
        }
    }

    @OnThread(Tag.Any)
    public static void logAndShowError(@LocalizableKey String actionKey, Exception ex)
    {
        _logAndShowError(actionKey, ex);
    }

    // I have no idea why, but this function causes an error if called directly from another module
    // but the proxy above does not:
    @OnThread(Tag.Any)
    private static void _logAndShowError(@LocalizableKey String actionKey, Exception ex)
    {
        @Localized String actionString = TranslationUtility.getString(actionKey);
        FXPlatformRunnable runAlert = () -> showError(actionString, (@Localized String s) -> Utility.universal(actionString + ": " + s), ex);
        if (Platform.isFxApplicationThread())
            ((Runnable)runAlert::run).run();
        else
            Platform.runLater(runAlert::run);
    }


    public static boolean isResizingRight(Cursor c)
    {
        return c == Cursor.NE_RESIZE || c == Cursor.E_RESIZE || c == Cursor.SE_RESIZE;
    }

    public static boolean isResizingLeft(Cursor c)
    {
        return c == Cursor.NW_RESIZE || c == Cursor.W_RESIZE || c == Cursor.SW_RESIZE;
    }

    public static boolean isResizingBottom(Cursor c)
    {
        return c == Cursor.SW_RESIZE || c == Cursor.S_RESIZE || c == Cursor.SE_RESIZE;
    }

    public static boolean isResizingTop(Cursor c)
    {
        return c == Cursor.NW_RESIZE || c == Cursor.N_RESIZE || c == Cursor.NE_RESIZE;
    }

    /**
     * Runs the given action once, either now or in the future, at the earliest
     * point that the item becomes non-null
     */
    public static <T> void onceNotNull(ObservableObjectValue<T> obsValue, FXPlatformConsumer<@NonNull T> action)
    {
        T initial = obsValue.get();
        if (initial != null)
            action.consume(initial);
        else
        {
            obsValue.addListener(new ChangeListener<T>()
            {
                @Override
                @OnThread(value = Tag.FXPlatform, ignoreParent = true)
                public void changed(ObservableValue<? extends T> a, T b, T newVal)
                {
                    if (newVal != null)
                    {
                        action.consume(newVal);
                        obsValue.removeListener(this);
                    }
                }
            });
        }
    }

    /**
     * Runs the given action once, either now or in the future, at the earliest
     * point that the item becomes non-null
     */
    public static void onceTrue(ObservableBooleanValue obsValue, FXPlatformRunnable action)
    {
        boolean initial = obsValue.get();
        if (initial)
            action.run();
        else
        {
            obsValue.addListener(new ChangeListener<Boolean>()
            {
                @Override
                @OnThread(value = Tag.FXPlatform, ignoreParent = true)
                public void changed(ObservableValue<? extends Boolean> a, Boolean b, Boolean newVal)
                {
                    if (newVal != null)
                    {
                        action.run();
                        obsValue.removeListener(this);
                    }
                }
            });
        }
    }

    /**
     * Choose a file.  The tag is used to store and recall the last
     * used directory for this type of file choice.
     * @param tag
     * @return
     */
    public static @Nullable File chooseFileOpen(@LocalizableKey String titleKey, String tag, Window parent, ExtensionFilter... extensionFilters)
    {
        FileChooser fc = new FileChooser();
        fc.setTitle(TranslationUtility.getString(titleKey));
        String initialDir = Utility.getProperty("recentdirs.txt", tag);
        if (initialDir != null)
            fc.setInitialDirectory(new File(initialDir));
        fc.getExtensionFilters().setAll(extensionFilters);
        File file = fc.showOpenDialog(parent);
        if (file != null)
        {
            @Nullable String parentDir = file.getAbsoluteFile().getParent();
            if (parentDir != null)
                Utility.setProperty("recentdirs.txt", tag, parentDir);
        }
        return file;
    }

    // TODO this seems to fall foul of checker, maybe try in 2.2.1?
    public static <R, T> void bindEager(Property<R> dest, List<ObservableValue<@NonNull ? extends T>> srcs, FXPlatformFunction<Stream<@NonNull T>, R> calculate)
    {
        FXPlatformConsumer<@NonNull T> listener = x -> dest.setValue(calculate.apply(srcs.stream().<@NonNull T>map((ObservableValue<@NonNull ? extends @NonNull T> e) -> e.getValue())));
        for (ObservableValue<@NonNull ? extends T> src : srcs)
        {
            //FXUtility.addChangeListenerPlatformNN(src, listener);
        }
    }
    
    public static <T> void listViewDoubleClick(ListView<T> listView, FXPlatformConsumer<T> onDoubleClick)
    {
        listView.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2)
            {
                @Nullable T selected = listView.getSelectionModel().getSelectedItem();
                if (selected != null)
                {
                    onDoubleClick.consume(selected);
                }
            }
        });
    }

    /**
     * Checks if a key typed event should actually proceed.
     * 
     * Returns false for things like backspace, control keys,
     * menu shortcuts and so on.
     * @param keyEvent
     * @return true if it should be handled, false if not
     */
    public static boolean checkKeyTyped(KeyEvent keyEvent)
    {
        // Borrowed from TextInputControlBehavior:
        // Sometimes we get events with no key character, in which case
        // we need to bail.
        String character = keyEvent.getCharacter();
        if (character.length() == 0)
            return false;

        // Filter out control keys except control+Alt on PC or Alt on Mac
        if (keyEvent.isControlDown() || keyEvent.isAltDown() || (SystemUtils.IS_OS_MAC && keyEvent.isMetaDown()))
        {
            if (!((keyEvent.isControlDown() || SystemUtils.IS_OS_MAC) && keyEvent.isAltDown()))
                return false;
        }

        // Ignore characters in the control range and the ASCII delete
        // character as well as meta key presses
        if (character.charAt(0) > 0x1F
                && character.charAt(0) != 0x7F
                && !keyEvent.isMetaDown()) // Not sure about this one (Note: this comment is in JavaFX source)
        {
            return true;
        }
        
        return false;
    }
    
    public static void setIcon(Stage stage)
    {
        ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();
        if (systemClassLoader != null)
        {
            for (int size : new int[]{16, 24, 32, 48, 64, 256})
            {

                InputStream icon = systemClassLoader.getResourceAsStream("logo-" + size + ".png");
                if (icon != null)
                {
                    stage.getIcons().add(new Image(icon));
                }
                else
                {
                    Log.error("Could not find file: logo-" + size + ".png");
                }
            }
        }
        else
            Log.error("No system classloader!");
    }

    public static interface GenOrError<T>
    {
        @OnThread(Tag.Simulation)
        T run() throws InternalException, UserException;
    }

    public static interface GenOrErrorFX<T>
    {
        @OnThread(Tag.FXPlatform)
        T run() throws InternalException, UserException;
    }

    public static interface RunOrErrorFX
    {
        @OnThread(Tag.FXPlatform)
        void run() throws InternalException, UserException;
    }
}
