package utility.gui;

import javafx.geometry.HPos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import org.checkerframework.checker.nullness.qual.Nullable;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by neil on 01/05/2017.
 */
@OnThread(Tag.FXPlatform)
public class LabelledGrid extends GridPane
{
    private int rows = 0;

    public static class Row
    {
        private final Label label;
        private final @Nullable HelpBox helpBox;
        private final Node item;

        public Row(Label label, @Nullable HelpBox helpBox, Node item)
        {
            this.label = label;
            this.helpBox = helpBox;
            this.item = item;
        }
    }

    public LabelledGrid()
    {
        getStyleClass().add("labelled-grid");
    }

    public int addRow(Row row)
    {
        int col = 0;
        add(row.label, col++, rows);
        setHalignment(row.label, HPos.RIGHT);
        if (row.helpBox != null)
            add(row.helpBox, col++, rows);
        add(row.item, col++, rows);
        return rows++;
    }


    public void clearRowsAfter(int rowNumber)
    {
        List<Node> childrenCopy = new ArrayList<>(getChildren());
        for (Node node : childrenCopy)
        {
            if (getRowIndex(node) > rowNumber)
                getChildren().remove(node);
        }
        rows = rowNumber + 1;
    }

}
