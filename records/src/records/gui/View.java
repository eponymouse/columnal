package records.gui;

import javafx.scene.layout.Pane;

import records.data.Record;
import records.data.RecordType;

/**
 * Created by neil on 18/10/2016.
 */
public class View extends Pane
{
    public View()
    {
        super(new Table(new Record(new RecordType())));
    }
}
