package utility.gui.stable;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.fxmisc.flowless.Cell;
import org.fxmisc.flowless.VirtualFlow;
import org.fxmisc.flowless.VirtualizedScrollPane;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.Arrays;
import java.util.Collections;

/**
 * A customised equivalent of TableView
 */
@OnThread(Tag.FXPlatform)
public class StableView
{
    private final VirtualFlow<@Nullable Object, StableRow> virtualFlow;
    private final VirtualizedScrollPane scrollPane;

    private final BooleanProperty showingRowNumbers = new SimpleBooleanProperty(true);


    public StableView()
    {
        virtualFlow = VirtualFlow.<@Nullable Object, StableRow>createVertical(FXCollections.observableArrayList(null, null, null), this::makeCell);
        scrollPane = new VirtualizedScrollPane<VirtualFlow<@Nullable Object, StableRow>>(virtualFlow);
    }

    private StableRow makeCell(@UnknownInitialization(Object.class) StableView this, @Nullable Object data)
    {
        return new StableRow();
    }

    public Node getNode()
    {
        return scrollPane;
    }


    @OnThread(Tag.FXPlatform)
    private class StableRow implements Cell<@Nullable Object, Node>
    {
        private final Label lineLabel = new Label();
        private final HBox hBox = new HBox(lineLabel, new Label("Data"));

        public StableRow()
        {
            lineLabel.visibleProperty().bind(showingRowNumbers);
            lineLabel.managedProperty().bind(lineLabel.visibleProperty());
        }

        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        public void updateIndex(int index)
        {
            lineLabel.setText(Integer.toString(index));
        }

        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        public Node getNode()
        {
            return hBox;
        }
    }

}
