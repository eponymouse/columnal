package utility.gui;

import javafx.geometry.HPos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.RadioButton;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by neil on 01/05/2017.
 */
@OnThread(Tag.FX)
public class LabelledGrid extends GridPane
{
    private int rows = 0;

    public static final class Row
    {
        private final Node lhs;
        private final @Nullable HelpBox helpBox;
        private final @Nullable Node item;

        public Row(Node label, @Nullable HelpBox helpBox, Node item)
        {
            setHalignment(label, HPos.RIGHT);
            this.lhs = label;
            this.helpBox = helpBox;
            this.item = item;
            GridPane.setHgrow(item, Priority.ALWAYS);
        }
        
        public Row(RadioButton radio, @Nullable HelpBox helpBox)
        {
            setHalignment(radio, HPos.LEFT);
            this.lhs = radio;
            this.helpBox = helpBox;
            this.item = null;
        }
        
        public Row(Node fullWidthNode)
        {
            this.lhs = fullWidthNode;
            this.helpBox = null;
            this.item = null;
        }
    }

    public LabelledGrid(Row... rows)
    {
        getStyleClass().add("labelled-grid");
        for (Row row : rows)
        {
            addRow(row);
        }
    }

    public int addRow(@UnknownInitialization(GridPane.class) LabelledGrid this, Row row)
    {
        int col = 0;
        add(row.lhs, col++, rows);
        if (row.helpBox != null)
            add(row.helpBox, col++, rows);
        if (row.item != null)
            add(row.item, col++, rows);
        return rows++;
    }
    
    // The return from the last addRow call
    public int getLastRow()
    {
        return rows - 1;
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
