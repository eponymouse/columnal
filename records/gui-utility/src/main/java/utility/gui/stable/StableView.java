package utility.gui.stable;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.fxmisc.flowless.Cell;
import org.fxmisc.flowless.VirtualFlow;
import org.fxmisc.flowless.VirtualizedScrollPane;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformFunction;
import utility.Pair;
import utility.SimulationFunction;
import utility.Utility;
import utility.Workers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * A customised equivalent of TableView
 */
@OnThread(Tag.FXPlatform)
public class StableView
{
    private final ObservableList<@Nullable Object> items;
    private final VirtualFlow<@Nullable Object, StableRow> virtualFlow;
    private final HBox header;
    private final VirtualizedScrollPane scrollPane;
    private final Label placeholder;
    private final StackPane stackPane; // has scrollPane and placeholder as its children

    private final BooleanProperty showingRowNumbers = new SimpleBooleanProperty(true);
    private final List<ValueFetcher> columns;


    public StableView()
    {
        items = FXCollections.observableArrayList();
        header = new HBox();
        virtualFlow = VirtualFlow.<@Nullable Object, StableRow>createVertical(items, this::makeCell);
        scrollPane = new VirtualizedScrollPane<VirtualFlow<@Nullable Object, StableRow>>(virtualFlow);
        placeholder = new Label("<Empty>");
        stackPane = new StackPane(placeholder, new BorderPane(scrollPane, header, null, null, null));
        // TODO make placeholder's visibility depend on table being empty
        columns = new ArrayList<>();


    }

    private StableRow makeCell(@UnknownInitialization(Object.class) StableView this, @Nullable Object data)
    {
        return new StableRow();
    }

    public Node getNode()
    {
        return stackPane;
    }

    public void setPlaceholderText(@Localized String text)
    {
        placeholder.setText(text);
    }

    public void clear()
    {
        // Clears rows, too:
        setColumns(Collections.emptyList());
    }

    public void setColumns(List<Pair<String, ValueFetcher>> columns)
    {
        items.clear();
        this.columns.clear();
        this.columns.addAll(Utility.mapList(columns, Pair::getSecond));
        header.getChildren().clear();
        for (Pair<String, ValueFetcher> column : columns)
        {
            header.getChildren().add(new Label(column.getFirst()));
        }
    }

    public void setRows(SimulationFunction<Integer, Boolean> isRowValid)
    {
        Workers.onWorkerThread("Calculating table rows", () -> {
            int toAdd = 0;
            try
            {
                outer:
                for (int i = 0; i < 10; i++)
                {
                    toAdd = 0;
                    for (int j = 0; j < 10; j++)
                    {
                        if (isRowValid.apply(i * 10 + j))
                        {
                            toAdd++;
                        } else
                            break outer;
                    }
                    int toAddFinal = toAdd;
                    Platform.runLater(() ->
                    {
                        for (int k = 0; k < toAddFinal; k++)
                        {
                            items.add(null);
                        }
                    });
                }
            }
            catch (InternalException | UserException e)
            {
                Utility.log(e);
                // TODO display somewhere?
            }
            int toAddFinal = toAdd;
            Platform.runLater(() -> {
                for (int k = 0; k < toAddFinal; k++)
                {
                    items.add(null);
                }
            });
        });

        //TODO store it for fetching more
    }

    @OnThread(Tag.FXPlatform)
    private class StableRow implements Cell<@Nullable Object, Node>
    {
        private final Label lineLabel = new Label();
        private final HBox hBox = new HBox(lineLabel);
        private int curIndex = -1;

        public StableRow()
        {
            lineLabel.visibleProperty().bind(showingRowNumbers);
            lineLabel.managedProperty().bind(lineLabel.visibleProperty());
        }

        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        public boolean isReusable()
        {
            return true;
        }

        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        public void updateIndex(int index)
        {
            if (index != curIndex)
            {
                curIndex = index;
                lineLabel.setText(Integer.toString(index));
                hBox.getChildren().setAll(lineLabel);
                Utility.logStackTrace("Columns: " + columns.size() + " index: " + index);
                for (ValueFetcher column : columns)
                {
                    int colIndex = hBox.getChildren().size();
                    hBox.getChildren().add(new Label(""));
                    column.fetchValue(index, (x, n) -> hBox.getChildren().set(colIndex, n));
                }
            }
        }

        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        public void updateItem(@Nullable Object item)
        {
            // Everything is actually done in updateIndex
        }

        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        public Node getNode()
        {
            return hBox;
        }
    }

    @OnThread(Tag.FXPlatform)
    public static interface ValueReceiver
    {
        public void setValue(int rowIndex, Node value);
    }

    @OnThread(Tag.FXPlatform)
    public static interface ValueFetcher
    {
        // Called to fetch a value.  Once available, received should be called.
        // Until then it will be blank.  You can call receiver multiple times though,
        // so you can just call it with a placeholder before returning.
        public void fetchValue(int rowIndex, ValueReceiver receiver);
    }
}
