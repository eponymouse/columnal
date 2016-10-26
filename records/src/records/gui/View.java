package records.gui;

import javafx.scene.layout.Pane;

import javafx.scene.layout.TilePane;
import records.data.Record;
import records.data.RecordSet;
import records.data.RecordType;

/**
 * Created by neil on 18/10/2016.
 */
public class View extends TilePane
{
    public View()
    {
    }

    public void add(Table table)
    {
        getChildren().add(table);
    }
}
