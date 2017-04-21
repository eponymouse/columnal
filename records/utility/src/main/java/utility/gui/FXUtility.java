package utility.gui;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableObjectValue;
import javafx.beans.value.ObservableValue;
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
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.util.StringConverter;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.controlsfx.validation.ValidationResult;
import org.jetbrains.annotations.NotNull;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.*;

import java.util.*;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by neil on 17/02/2017.
 */
@OnThread(Tag.FXPlatform)
public class FXUtility
{
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
        listView.setOnDragDetected(e -> {
            Dragboard db = listView.startDragAndDrop(transferMode);
            List<T> selected = new ArrayList<>(listView.getSelectionModel().getSelectedItems());
            db.setContent(Collections.singletonMap(getTextDataFormat(type), selected));
            e.consume();
        });
    }

    public static @NotNull DataFormat getTextDataFormat(String subType)
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
        return Stream.concat(
                   Stream.of("general.css"),
                   Arrays.stream(stylesheetNames).map(s -> s.endsWith(".css") ? s : (s + ".css"))
                 )
                 .distinct()
                 .map(Utility::getStylesheet)
                 .collect(Collectors.<String>toList());
    }

    public static interface DragHandler
    {
        @OnThread(Tag.FXPlatform)
        void dragMoved(Point2D pointInScene);

        @OnThread(Tag.FXPlatform)
        boolean dragEnded(Dragboard dragboard, Point2D pointInScene);
    }

    // Point is in Scene
    public static void enableDragTo(Node destination, Map<DataFormat, DragHandler> receivers)
    {
        destination.setOnDragOver(e -> {
            boolean accepts = false;
            for (Entry<DataFormat, DragHandler> receiver : receivers.entrySet())
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
            for (Entry<DataFormat, DragHandler> receiver : receivers.entrySet())
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

    public static <T, R> ObjectExpression<R> mapBindingEager(ObservableObjectValue<T> original, FXPlatformFunction<T, R> extract)
    {
        ObjectProperty<R> binding = new SimpleObjectProperty<>();
        Utility.addChangeListenerPlatformNN(original, x -> binding.setValue(extract.apply(x)));
        return binding;
    }

    public static void setPseudoclass(Node node, String className, boolean on)
    {
        node.pseudoClassStateChanged(PseudoClass.getPseudoClass(className), on);
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
        @OnThread(Tag.Any) @Localized String actionString = TranslationUtility.getString(actionKey);
        Utility.log(actionString, ex);
        FXPlatformRunnable runAlert = () -> new Alert(AlertType.ERROR, actionString + "\n" + (ex.getLocalizedMessage() == null ? "" : ex.getLocalizedMessage()), ButtonType.OK).showAndWait();
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
}
