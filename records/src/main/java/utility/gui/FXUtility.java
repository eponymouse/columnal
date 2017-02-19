package utility.gui;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectExpression;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.ListView;
import javafx.scene.control.cell.TextFieldListCell;
import javafx.scene.input.DataFormat;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.jetbrains.annotations.NotNull;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformBiConsumer;
import utility.FXPlatformConsumer;
import utility.FXPlatformFunction;
import utility.Utility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.function.Function;

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

    public static <T, R> ObjectExpression<R> mapBinding(ObjectExpression<T> original, FXPlatformFunction<T, R> extract)
    {
        return Bindings.createObjectBinding(() -> extract.apply(original.get()), original);
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
}
