package records.transformations;

import javafx.beans.binding.BooleanExpression;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ObservableObjectValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.TextFieldListCell;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.StringConverter;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.ColumnId;
import records.data.Table;
import records.data.TableId;
import records.data.TableManager;
import records.data.Transformation;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.FXPlatformRunnable;
import utility.Pair;
import utility.SimulationSupplier;
import utility.Utility;
import utility.gui.FXUtility;
import utility.gui.GUI;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by neil on 22/11/2016.
 */
@OnThread(Tag.FXPlatform)
public abstract class TransformationEditor
{
    /**
     * Gets the info on the transformation
     * @return
     */
    public abstract TransformationInfo getInfo();

    /**
     * The title to show at the top of the information display.
     */
    public abstract @Localized String getDisplayTitle();

    /**
     * The description to show at the top of the information display.
     *
     * Returns one key for the short version, and one for the remaining text.
     */
    public abstract Pair<@LocalizableKey String, @LocalizableKey String> getDescriptionKeys();

    public abstract Pane getParameterDisplay(FXPlatformConsumer<Exception> reportError);

    /**
     * Get a simulation-thread maker for the resulting transformation.  If null
     * is returned, it is not valid to press OK at the moment, and an error should
     * be displayed.
     */
    public abstract @Nullable SimulationSupplier<Transformation> getTransformation(TableManager mgr, @Nullable TableId newId);

    public abstract @Nullable TableId getSourceId();

    protected static ListView<ColumnId> getColumnListView(TableManager mgr, ObjectExpression<@Nullable TableId> idProperty, @Nullable ObservableList<ColumnId> exclude, @Nullable FXPlatformConsumer<ColumnId> onDoubleClick)
    {
        ListView<ColumnId> lv = new ListView<>();
        lv.setPlaceholder(GUI.labelWrapParam("transformEditor.columnList.noSuchTable", ""));
        FXPlatformRunnable update = () -> {
            @Nullable TableId id = idProperty.get();
            updateList(lv, id, id == null ? null : mgr.getSingleTableOrNull(id), exclude);
        };
        FXUtility.addChangeListenerPlatform(idProperty, id -> update.run());
        if (exclude != null)
            FXUtility.listen(exclude, c -> update.run());
        // Also run initially:
        update.run();

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

    private static void updateList(ListView<ColumnId> lv, @Nullable TableId id, @Nullable Table src, @Nullable List<ColumnId> exclude)
    {
        lv.setPlaceholder(GUI.labelWrapParam("transformEditor.columnList.noSuchTable", id == null ? "" : id.getOutput()));
        if (src != null)
        {
            try
            {
                List<ColumnId> columnIds = new ArrayList<>(src.getData().getColumnIds());
                if (exclude != null)
                    columnIds.removeAll(exclude);
                lv.setItems(FXCollections.observableArrayList(columnIds));
                if (lv.getItems().isEmpty())
                {
                    lv.setPlaceholder(GUI.labelWrapParam("transformEditor.columnList.noColumns", id == null ? "" : id.getOutput()));
                }
            }
            catch (UserException e)
            {
                lv.setPlaceholder(GUI.labelWrapParam("transformEditor.columnList.noSuchTable.user", id == null ? "" : id.getOutput(), e.getLocalizedMessage()));
            }
            catch (InternalException e)
            {
                Utility.report(e);
                lv.setPlaceholder(GUI.labelWrapParam("transformEditor.columnList.noSuchTable.internal", id == null ? "" : id.getOutput(), e.getLocalizedMessage()));
            }
        }
    }
/*
    protected static TableView<List<DisplayValue>> createExampleTable(ObservableList<Pair<String, List<DisplayValue>>> headerAndData)
    {
        TableView<List<DisplayValue>> t = new TableView<>();
        setHeaderAndData(t, headerAndData);
        FXUtility.listen(headerAndData, c -> setHeaderAndData(t, headerAndData));
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
*/
}
