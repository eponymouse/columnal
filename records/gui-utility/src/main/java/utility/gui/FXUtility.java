package utility.gui;

import com.google.common.collect.ImmutableList;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanExpression;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableBooleanValue;
import javafx.beans.value.ObservableObjectValue;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.cell.TextFieldListCell;
import javafx.scene.input.DataFormat;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Region;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Modality;
import javafx.stage.Window;
import javafx.util.Duration;
import javafx.util.StringConverter;
import log.Log;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.KeyFor;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.UnknownKeyFor;
import org.checkerframework.dataflow.qual.Pure;
import org.controlsfx.validation.ValidationResult;
import records.error.InternalException;
import records.error.UserException;
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
        DataFormat f = DataFormat.lookupMimeType(whole);
        if (f != null)
            return f;
        else
            return new DataFormat(whole);
    }

    @OnThread(Tag.FX)
    public static void sizeToFit(TextField tf, @Nullable Double minSizeFocused, @Nullable Double minSizeUnfocused)
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
                    + tf.getInsets().getLeft() + + tf.getInsets().getRight()
                    + 5d; // Add some spacing
                return Math.max(tf.isFocused() ? (minSizeFocused == null ? 20 : minSizeFocused) : (minSizeUnfocused == null ? 20 : minSizeUnfocused), width);
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
                   Stream.of("general.css", "stableview.css"),
                   Arrays.stream(stylesheetNames).map(s -> s.endsWith(".css") ? s : (s + ".css"))
                 )
                 .distinct()
                 .map(FXUtility::getStylesheet)
                 .collect(Collectors.<String>toList());
    }

    public static ExtensionFilter getProjectExtensionFilter()
    {
        return new ExtensionFilter(TranslationUtility.getString("extension.projects"), "*.rec");
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
    public static <T> void addChangeListenerPlatform(ObservableValue<T> property, FXPlatformConsumer<@Nullable T> listener)
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
        if (testingMode)
            return new TextInputDialog().showAndWait().map(File::new).orElse(null);
        else
            return new FileChooser().showSaveDialog(parent.getScene() == null ? null : parent.getScene().getWindow());
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
    public static void alertOnError_(RunOrError r)
    {
        alertOnError_(err -> err, r);
    }

    @OnThread(Tag.Simulation)
    public static void alertOnError_(Function<@Localized String, @Localized String> errWrap, RunOrError r)
    {
        try
        {
            r.run();
        }
        catch (InternalException | UserException e)
        {
            Platform.runLater(() ->
            {
                showError(errWrap, e);
            });
        }
    }

    @OnThread(Tag.FXPlatform)
    public static void alertOnErrorFX_(RunOrErrorFX r)
    {
        try
        {
            r.run();
        }
        catch (InternalException | UserException e)
        {
            showError(e);
        }
    }

    @OnThread(Tag.FXPlatform)
    public static <T> @Nullable T alertOnErrorFX(GenOrErrorFX<T> r)
    {
        try
        {
            return r.run();
        }
        catch (InternalException | UserException e)
        {
            showError(e);
            return null;
        }
    }

    @OnThread(Tag.FXPlatform)
    public static void showError(Exception e)
    {
        showError(x -> x, e);
    }

    @OnThread(Tag.FXPlatform)
    public static void showError(Function<@Localized String, @Localized String> errWrap, Exception e)
    {
        if (showingError)
        {
            // TODO do something with the queued errors; add to shown dialog?
            queuedErrors.add(e);
        }
        else
        {
            Log.log(e);
            // Don't show dialog which would interrupt a JUnit test:
            if (!isJUnitTest())
            {
                String localizedMessage = errWrap.apply(e.getLocalizedMessage());
                Alert alert = new Alert(AlertType.ERROR, localizedMessage == null ? "Unknown error" : localizedMessage, ButtonType.OK);
                alert.initModality(Modality.APPLICATION_MODAL);
                showingError = true;
                alert.showAndWait();
                showingError = false;
            }
        }
    }

    @OnThread(Tag.Simulation)
    public static <T> Optional<T> alertOnError(GenOrError<@Nullable T> r)
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
                showError(e);
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
    public static void setPseudoclass(Node node, String className, boolean on)
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
        FXPlatformRunnable runAlert = () -> showError((@Localized String s) -> Utility.universal(actionString + ": " + s), ex);
        if (Platform.isFxApplicationThread())
            ((Runnable)runAlert::run).run();
        else
            Platform.runLater(runAlert::run);
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

    public static interface GenOrError<T>
    {
        @OnThread(Tag.Simulation)
        T run() throws InternalException, UserException;
    }

    public static interface RunOrError
    {
        @OnThread(Tag.Simulation)
        void run() throws InternalException, UserException;
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
