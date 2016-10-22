package records.data;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;

import org.checkerframework.checker.nullness.qual.NonNull;
import records.error.UserException;
import utility.Utility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Created by neil on 20/10/2016.
 */
public class RecordSet
{
    private final String title;
    private List<Column> columns;
    private int knownMinCount;

    public RecordSet(String title, List<Column> columns, int knownMinCount)
    {
        this.title = title;
        this.columns = columns;
        this.knownMinCount = knownMinCount;
    }

    public List<TableColumn<Integer, String>> getDisplayColumns()
    {
        Function<@NonNull Column, @NonNull TableColumn<Integer, String>> makeDisplayColumn = data ->
        {
            TableColumn<Integer, String> c = new TableColumn<>(data.getName());
            c.setCellValueFactory(cdf ->
            {
                try
                {
                    return new ReadOnlyStringWrapper(data.get(cdf.getValue()).toString());
                }
                catch (Exception ex)
                {
                    ex.printStackTrace();
                    return new ReadOnlyStringWrapper("");
                }
            });
            return c;
        };
        return Utility.mapList(columns, makeDisplayColumn);
    }

    public int getCurrentKnownMinRows()
    {
        return knownMinCount;
    }

    public String getTitle()
    {
        return title;
    }

    public Column getColumn(String name) throws UserException
    {
        for (Column c : columns)
        {
            if (c.getName().equals(name))
                return c;
        }
        throw new UserException("Column not found");
    }

    public List<Column> getColumns()
    {
        return Collections.unmodifiableList(columns);
    }
}
