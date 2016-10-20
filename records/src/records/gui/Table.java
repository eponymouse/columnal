package records.gui;

import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;

import records.data.Record;
import records.data.RecordSet;

/**
 * Created by neil on 18/10/2016.
 */
public class Table extends BorderPane
{
    private static class TableDisplay extends TableView<Integer>
    {
        public TableDisplay(RecordSet recordSet)
        {
            getColumns().setAll(recordSet.getDisplayColumns());
            for (int i = 0; i < recordSet.getCurrentKnownMinRows(); i++)
                getItems().add(Integer.valueOf(i));
        }
    }

    public Table(RecordSet rs)
    {
        super(new TableDisplay(rs));
    }
}
