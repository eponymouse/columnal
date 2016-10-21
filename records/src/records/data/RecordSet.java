package records.data;

import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import org.jetbrains.annotations.NotNull;
import utility.Utility;

import java.util.List;

/**
 * Created by neil on 20/10/2016.
 */
public class RecordSet
{
    private final String title;
    private List<Column<?>> columns;
    private int knownMinCount;

    public RecordSet(String title, List<Column<?>> columns, int knownMinCount)
    {
        this.title = title;
        this.columns = columns;
        this.knownMinCount = knownMinCount;
    }

    @NotNull public List<TableColumn<Integer, String>> getDisplayColumns()
    {
        return Utility.mapList(columns, data -> {
            TableColumn<Integer, String> c = new TableColumn(data.getName());
            c.setCellFactory(col ->
            {
                // TODO is it necessary to use a custom cell?  I think
                // custom cell value factory would be enough now that we use an index
                // as a data item.
                return new TableCell<Integer, String>() {
                    // The Strings will always be null, but they may have changed:
                    @Override
                    protected boolean isItemChanged(String oldItem, String newItem)
                    {
                        return true;
                    }

                    @Override
                    protected void updateItem(String item, boolean empty)
                    {
                        if (empty || getIndex() == -1)
                        {
                            setText("");
                            super.updateItem(null, empty);
                            return;
                        }

                        String val = "ERROR";
                        try
                        {
                            val = data.get(getIndex()).toString();
                        }
                        catch (Exception ex)
                        {
                            ex.printStackTrace();
                        }
                        setText(val);
                        super.updateItem(val, empty);
                    }
                };
            });
            return c;
        });
    }

    public int getCurrentKnownMinRows()
    {
        return knownMinCount;
    }

    @NotNull public String getTitle()
    {
        return title;
    }

    @NotNull public Column getColumn(String name) throws Exception
    {
        for (Column c : columns)
        {
            if (c.getName().equals(name))
                return c;
        }
        throw new Exception("Column not found");
    }
}
