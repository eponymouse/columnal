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
import records.data.ColumnId;
import records.data.Table;
import records.data.TableId;
import records.data.TableManager;
import records.data.Transformation;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.SimulationSupplier;

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

    @OnThread(Tag.FXPlatform)
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
}
