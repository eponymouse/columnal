package records.transformations;

import javafx.beans.binding.BooleanExpression;
import javafx.beans.binding.StringExpression;
import javafx.collections.FXCollections;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.cell.TextFieldListCell;
import javafx.scene.layout.Pane;
import javafx.util.StringConverter;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Column;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.Table;
import records.data.TableId;
import records.data.TableManager;
import records.data.Transformation;
import records.error.InternalException;
import records.error.UserException;
import records.gui.TableDisplay;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.SimulationSupplier;

import java.util.List;

/**
 * Created by neil on 02/11/2016.
 */
public abstract class TransformationInfo
{
    /**
     * The name, as will be shown in the search bar and used for saving and loading.
     */
    protected final String name;
    /**
     * Keywords to search (e.g. alternative names for this function).
     */
    protected final List<String> keywords;

    @OnThread(Tag.Any)
    public TransformationInfo(String name, List<String> keywords)
    {
        this.name = name;
        this.keywords = keywords;
    }

    public final String getName()
    {
        return name;
    }

    @OnThread(Tag.Simulation)
    public abstract Transformation load(TableManager mgr, TableId tableId, String detail) throws InternalException, UserException;

    @OnThread(Tag.FXPlatform)
    public abstract TransformationEditor editNew(TableId srcTableId, @Nullable Table src);

    @OnThread(Tag.FXPlatform)
    public abstract static class TransformationEditor
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

        @OnThread(Tag.FXPlatform)
        protected static ListView<ColumnId> getColumnListView(@Nullable Table src, TableId id)
        {
            ListView<ColumnId> lv = new ListView<>();
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
            }
            else
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
    }
}
