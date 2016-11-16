package records.transformations;

import javafx.beans.binding.BooleanExpression;
import javafx.collections.FXCollections;
import javafx.scene.control.ListView;
import javafx.scene.control.cell.TextFieldListCell;
import javafx.scene.layout.Pane;
import javafx.util.StringConverter;
import records.data.Column;
import records.data.RecordSet;
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
    /**
     * The title to show at the top of the information display.
     */
    protected final String displayTitle;

    @OnThread(Tag.Any)
    public TransformationInfo(String name, List<String> keywords, String displayTitle)
    {
        this.name = name;
        this.keywords = keywords;
        this.displayTitle = displayTitle;
    }

    public String getName()
    {
        return name;
    }

    @OnThread(Tag.FXPlatform)
    public abstract Pane getParameterDisplay(TableDisplay src, FXPlatformConsumer<Exception> reportError);

    @OnThread(Tag.FXPlatform)
    public abstract BooleanExpression canPressOk();

    @OnThread(Tag.FXPlatform)
    public abstract SimulationSupplier<Transformation> getTransformation(TableManager mgr);

    @OnThread(Tag.Simulation)
    public abstract Transformation load(TableManager mgr, TableId tableId, String detail) throws InternalException, UserException;

    public String getDisplayTitle()
    {
        return displayTitle;
    }

    @OnThread(Tag.FXPlatform)
    protected static ListView<Column> getColumnListView(RecordSet src)
    {
        ListView<Column> lv = new ListView<>(FXCollections.observableArrayList(src.getColumns()));
        lv.setCellFactory(lv2 -> new TextFieldListCell<Column>(new StringConverter<Column>()
        {
            @Override
            public String toString(Column col)
            {
                return col.getName().toString();
            }

            @Override
            public Column fromString(String string)
            {
                throw new UnsupportedOperationException();
            }
        }));
        lv.setEditable(false);
        return lv;
    }
}
