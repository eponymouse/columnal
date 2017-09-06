package records.transformations;

import javafx.beans.binding.BooleanExpression;
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
        lv.setPlaceholder(GUI.labelWrapParam("transformEditor.columnList.noSuchTable", ""));
        FXUtility.addChangeListenerPlatform(idProperty, id -> updateList(lv, id, id == null ? null : mgr.getSingleTableOrNull(id)));
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
        lv.setPlaceholder(GUI.labelWrapParam("transformEditor.columnList.noSuchTable", id == null ? "" : id.getOutput()));
        if (src != null)
        {
            try
            {
                lv.setItems(FXCollections.observableArrayList(src.getData().getColumnIds()));
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
