package records.transformations;

import javafx.beans.binding.BooleanExpression;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ObservableObjectValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.PopupControl;
import javafx.scene.control.Skin;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.TextFieldListCell;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.PopupWindow.AnchorLocation;
import javafx.util.StringConverter;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.ColumnId;
import records.data.Table;
import records.data.TableId;
import records.data.TableManager;
import records.data.Transformation;
import records.error.InternalException;
import records.error.UserException;
import records.gui.DisplayValue;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.SimulationSupplier;
import utility.Utility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Created by neil on 22/11/2016.
 */
@OnThread(Tag.FXPlatform)
public abstract class TransformationEditor
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
                    ResourceBundle.getBundle("function")
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
    public static @Localized String getString(@LocalizableKey String key)
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
                        return local;
                }
                catch (MissingResourceException e)
                {
                    // This is fine; just try the next one.
                }
            }
        }

        return key; // Best we can do, if we can't find the labels file.
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
            Utility.addChangeListenerPlatformNN(link.hoverProperty(), new FXPlatformConsumer<Boolean>()
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
                                public PopupControl getSkinnable()
                                {
                                    return ctrl;
                                }

                                @Override
                                public Node getNode()
                                {
                                    return label;
                                }

                                @Override
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
     * The title to show at the top of the information display.
     */
    public abstract String getDisplayTitle();

    /**
     * The description to show at the top of the information display.
     */
    public @Localized String getDescription()
    {
        return ""; // TODO make this abstract once I've written the descriptions.
    }

    public abstract Pane getParameterDisplay(FXPlatformConsumer<Exception> reportError);

    public abstract BooleanExpression canPressOk();

    public abstract SimulationSupplier<Transformation> getTransformation(TableManager mgr);

    public abstract @Nullable TableId getSourceId();

    protected static ListView<ColumnId> getColumnListView(TableManager mgr, ObservableObjectValue<@Nullable TableId> idProperty, @Nullable FXPlatformConsumer<ColumnId> onDoubleClick)
    {
        ListView<ColumnId> lv = new ListView<>();
        lv.setPlaceholder(new Label("Could not find table: " + idProperty.get()));
        Utility.addChangeListenerPlatform(idProperty, id -> updateList(lv, id, id == null ? null : mgr.getSingleTableOrNull(id)));
        {
            @Nullable TableId id = idProperty.get();
            updateList(lv, id, id == null ? null : mgr.getSingleTableOrNull(id));
        }
        lv.setCellFactory(lv2 -> new TextFieldListCell<ColumnId>(new StringConverter<ColumnId>()
        {
            @Override
            public String toString(ColumnId col)
            {
                return col.toString();
            }

            @Override
            public ColumnId fromString(String string)
            {
                throw new UnsupportedOperationException();
            }
        }));

        lv.setEditable(false);

        if (onDoubleClick != null)
        {
            FXPlatformConsumer<ColumnId> handler = onDoubleClick;
            lv.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2)
                {
                    ColumnId selectedItem = lv.getSelectionModel().getSelectedItem();
                    if (selectedItem != null)
                        handler.consume(selectedItem);
                }
            });
        }

        return lv;
    }

    private static void updateList(ListView<ColumnId> lv, @Nullable TableId id, @Nullable Table src)
    {
        lv.setPlaceholder(new Label("Could not find table: " + id));
        if (src != null)
        {
            try
            {
                lv.setItems(FXCollections.observableArrayList(src.getData().getColumnIds()));
            }
            catch (UserException e)
            {
                lv.setPlaceholder(new Label("Could not find table: " + id + "\n" + e.getLocalizedMessage()));
            }
            catch (InternalException e)
            {
                Utility.report(e);
                lv.setPlaceholder(new Label("Could not find table due to internal error: " + id + "\n" + e.getLocalizedMessage()));
            }
        }
    }

    protected static TableView<List<DisplayValue>> createExampleTable(ObservableList<Pair<String, List<DisplayValue>>> headerAndData)
    {
        TableView<List<DisplayValue>> t = new TableView<>();
        setHeaderAndData(t, headerAndData);
        Utility.listen(headerAndData, c -> setHeaderAndData(t, headerAndData));
        return t;
    }

    private static void setHeaderAndData(TableView<List<DisplayValue>> t, List<Pair<String, List<DisplayValue>>> headerAndData)
    {
        t.getColumns().clear();
        t.getItems().clear();
        List<List<DisplayValue>> rows = new ArrayList<>();
        // Arrange into rows:
        for (Pair<String, List<DisplayValue>> h : headerAndData)
        {
            for (int i = 0; i < h.getSecond().size(); i++)
            {
                while (rows.size() <= i)
                    rows.add(new ArrayList<>());
                rows.get(i).add(h.getSecond().get(i));
            }
        }
        t.getItems().setAll(rows);
        
        for (int i = 0; i < headerAndData.size(); i++)
        {
            Pair<String, List<DisplayValue>> h = headerAndData.get(i);
            TableColumn<List<DisplayValue>, DisplayValue> column = new TableColumn<List<DisplayValue>, DisplayValue>(h.getFirst());
            int colIndex = i;
            column.setCellValueFactory(cdf -> new ReadOnlyObjectWrapper<>(cdf.getValue().get(colIndex)));
            t.getColumns().add(column);
        }
    }

    protected static Node createExplanation(ObservableList<Pair<String, List<DisplayValue>>> srcHeaderAndData, ObservableList<Pair<String, List<DisplayValue>>> destHeaderAndData, String explanation)
    {
        TextFlow textFlow = new TextFlow(new Text(explanation));
        textFlow.setPrefWidth(500);
        return new BorderPane(new Label("->"), null, createExampleTable(destHeaderAndData), textFlow, createExampleTable(srcHeaderAndData));
    }
}
