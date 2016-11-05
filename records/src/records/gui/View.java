package records.gui;

import javafx.scene.layout.Pane;
import javafx.scene.layout.TilePane;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Created by neil on 18/10/2016.
 */
public class View extends Pane
{
    private static final double DEFAULT_SPACE = 150.0;

    public View()
    {
    }

    public void add(Table table, @Nullable Table alignToRightOf)
    {
        getChildren().add(table);
        if (alignToRightOf != null)
        {
            table.setLayoutX(alignToRightOf.getLayoutX() + alignToRightOf.getWidth() + DEFAULT_SPACE);
            table.setLayoutY(alignToRightOf.getLayoutY());
        }
    }

    @Override
    public void requestFocus()
    {
        // Don't allow focus
    }
}
