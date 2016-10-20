package records.gui;

import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;

import records.data.Record;

/**
 * Created by neil on 18/10/2016.
 */
public class Table extends BorderPane
{
    private static class TableDisplay extends TableView<Record>
    {
        public TableDisplay(Record record)
        {
            getColumns().setAll(record.getType().getColumns());
        }
    }

    public Table(Record r)
    {
        super(new TableDisplay(r));
    }
}
