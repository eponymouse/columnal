package records.data;

import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;

import org.checkerframework.checker.guieffect.qual.UIEffect;
import org.checkerframework.checker.nullness.qual.NonNull;
import records.error.UserException;
import records.gui.DisplayValue;
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

    @UIEffect
    public List<TableColumn<Integer, DisplayValue>> getDisplayColumns()
    {
        Function<@NonNull Column, @NonNull TableColumn<Integer, DisplayValue>> makeDisplayColumn = data ->
        {
            TableColumn<Integer, DisplayValue> c = new TableColumn<>(data.getName());
            c.setCellValueFactory(cdf ->
            {
                //try
                //{
                    ObservableValue<DisplayValue> val = data.getDisplay(cdf.getValue());
                    //return Bindings.convert(val);
                    return val;
                //}
                //catch (Exception ex)
                //{
                    //ex.printStackTrace();
                    //return new ReadOnlyStringWrapper("");
                //}
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
