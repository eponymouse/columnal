package records.gui;

import javafx.scene.layout.TilePane;

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
