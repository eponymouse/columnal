package records.data;

import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;

import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.text.Text;
import org.checkerframework.checker.nullness.qual.NonNull;
import records.error.FunctionInt;
import records.error.InternalException;
import records.error.UserException;
import records.gui.DisplayValue;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A RecordSet is a collection of columns.
 *
 * RecordSet assumptions:
 *
 *  - All of the columns in RecordSet have the same number of entries.
 *    A RecordSet will not be bottom-ragged.
 *  - This number of entries is not known a priori.  See Column.indexValid
 *    for a discussion of that method.
 *  - Columns are otherwise treated independently.  Just because one column
 *    value is loaded doesn't mean that any values in any other columns
 *    will/won't be loaded.
 */
public abstract class RecordSet
{
    @OnThread(Tag.Any)
    private final String title;
    @OnThread(Tag.Any)
    private final List<Column> columns;

    @SuppressWarnings("initialization")
    public RecordSet(String title, List<FunctionInt<RecordSet, Column>> columns) throws InternalException, UserException
    {
        this.title = title;
        this.columns = new ArrayList<>();
        for (FunctionInt<RecordSet, Column> f : columns)
            this.columns.add(f.apply(this));
    }

    @OnThread(Tag.FXPlatform)
    public final List<TableColumn<Integer, DisplayValue>> getDisplayColumns()
    {
        Function<@NonNull Column, @NonNull TableColumn<Integer, DisplayValue>> makeDisplayColumn = data ->
        {
            TableColumn<Integer, DisplayValue> c = new TableColumn<>(data.getName().toString());
            c.setCellValueFactory(cdf -> data.getDisplay(cdf.getValue()));
            c.setCellFactory(col -> {
                return new TableCell<Integer, DisplayValue>() {
                    @Override
                    @OnThread(Tag.FX)
                    protected void updateItem(DisplayValue item, boolean empty)
                    {
                        super.updateItem(item, empty);
                        if (item == null)
                        {
                            setText("");
                            setGraphic(null);
                        }
                        else if (item.getNumber() != null)
                        {
                            @NonNull Number n = item.getNumber();
                            setText("");
                            HBox container = new HBox();
                            Utility.addStyleClass(container, "number-display");
                            Text prefix = new Text(item.getDisplayPrefix());
                            Utility.addStyleClass(prefix, "number-display-prefix");
                            String integerPart = Utility.getIntegerPart(n);
                            integerPart = integerPart.replace("-", "\u2012");
                            Text whole = new Text(integerPart);
                            Utility.addStyleClass(whole, "number-display-int");
                            String fracPart = Utility.getFracPart(n);
                            while (fracPart.length() < item.getMinimumDecimalPlaces())
                                fracPart += "0";
                            Text frac = new Text(fracPart.isEmpty() ? "" : ("." + fracPart));
                            Utility.addStyleClass(frac, "number-display-frac");
                            Pane spacer = new Pane();
                            spacer.setVisible(false);
                            HBox.setHgrow(spacer, Priority.ALWAYS);
                            container.getChildren().addAll(prefix, spacer, whole, frac);
                            setGraphic(container);
                        }
                        else
                        {
                            setGraphic(null);
                            setText(item.toString());
                        }
                    }
                };
            });
            c.setSortable(false);
            data.withDisplay(type -> {
                c.setText("");
                c.setGraphic(new Label(type + "\n" + data.getName()));
            });
            return c;
        };
        return Utility.mapList(columns, makeDisplayColumn);
    }

    @OnThread(Tag.Any)
    public final String getTitle()
    {
        return title;
    }

    @OnThread(Tag.Any)
    public final Column getColumn(ColumnId name) throws UserException
    {
        for (Column c : columns)
        {
            if (c.getName().equals(name))
                return c;
        }
        throw new UserException("Column not found");
    }

    //package-protected:
    public abstract boolean indexValid(int index) throws UserException, InternalException;

    // Only use when you really need to know the length!
    // Override in subclasses if you can do it faster
    public int getLength() throws UserException, InternalException
    {
        int i = 0;
        while (indexValid(i))
        {
            i += 1;
        }
        return i;
    }

    @OnThread(Tag.Any)
    public final List<Column> getColumns()
    {
        return Collections.unmodifiableList(columns);
    }

    public String debugGetVals(int i)
    {
        return columns.stream().map(c -> { try
        {
            return "\"" + c.getType().getCollapsed(i).toString() + "\"";
        }catch (Exception e) { return "ERR"; }}).collect(Collectors.joining(","));
    }

    @OnThread(Tag.Any)
    public final List<ColumnId> getColumnIds()
    {
        return Utility.<Column, ColumnId>mapList(columns, Column::getName);
    }
}
