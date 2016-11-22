package records.transformations;

import javafx.beans.binding.BooleanExpression;
import javafx.beans.binding.StringExpression;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
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
import javafx.util.StringConverter;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.ColumnId;
import records.data.Table;
import records.data.TableId;
import records.data.TableManager;
import records.data.Transformation;
import records.error.UserException;
import records.gui.DisplayValue;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.SimulationSupplier;

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
    @OnThread(Tag.FX)
    public abstract StringExpression displayTitle();

    public abstract Pane getParameterDisplay(FXPlatformConsumer<Exception> reportError);

    public abstract BooleanExpression canPressOk();

    public abstract SimulationSupplier<Transformation> getTransformation(TableManager mgr);

    public abstract @Nullable Table getSource();

    public abstract TableId getSourceId();

    protected static ListView<ColumnId> getColumnListView(@Nullable Table src, TableId id)
    {
        ListView<ColumnId> lv = new ListView<>();
        if (src != null)
        {
            try
            {
                lv.setItems(FXCollections.observableArrayList(src.getData().getColumnIds()));
            } catch (UserException e)
            {
                lv.setPlaceholder(new Label("Could not find table: " + id + "\n" + e.getLocalizedMessage()));
            }
        } else
        {
            lv.setPlaceholder(new Label("Could not find table: " + id));
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
        return lv;
    }

    @SuppressWarnings({"keyfor", "intern"})
    protected static TableView<List<DisplayValue>> createExampleTable(ObservableList<List<DisplayValue>> headerAndData)
    {
        TableView<List<DisplayValue>> t = new TableView<>();
        setHeaderAndData(t, headerAndData);
        headerAndData.addListener((ListChangeListener<? super List<DisplayValue>>) c -> setHeaderAndData(t, headerAndData));
        return t;
    }

    private static void setHeaderAndData(TableView<List<DisplayValue>> t, ObservableList<List<DisplayValue>> headerAndData)
    {
        t.getColumns().clear();
        t.getItems().clear();
        for (int i = 0; i < headerAndData.size(); i++)
        {
            if (i == 0)
            {
                List<DisplayValue> header = headerAndData.get(i);
                for (int j = 0; j < header.size(); j++)
                {
                    int jFinal = j;
                    TableColumn<List<DisplayValue>, DisplayValue> column = new TableColumn<List<DisplayValue>, DisplayValue>(header.get(j).toString());
                    column.setCellValueFactory(cdf -> new ReadOnlyObjectWrapper(cdf.getValue().get(jFinal)));
                    t.getColumns().add(column);
                }
            }
            else
                t.getItems().add(headerAndData.get(i));
        }
    }

    protected static Node createExplanation(ObservableList<List<DisplayValue>> srcHeaderAndData, ObservableList<List<DisplayValue>> destHeaderAndData, String explanation)
    {
        return new BorderPane(new Label("->"), null, createExampleTable(destHeaderAndData), new Text(explanation), createExampleTable(srcHeaderAndData));
    }
}
