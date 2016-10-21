package records.gui;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;

import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;

import org.checkerframework.checker.initialization.qual.UnderInitialization;
import records.data.Record;
import records.data.RecordSet;
import records.data.SummaryStatistics;

/**
 * Created by neil on 18/10/2016.
 */
public class Table extends BorderPane
{
    private static class TableDisplay extends TableView<Integer>
    {
        @SuppressWarnings("initialization")
        public TableDisplay(RecordSet recordSet)
        {
            super();
            getColumns().setAll(recordSet.getDisplayColumns());
            for (int i = 0; i < recordSet.getCurrentKnownMinRows(); i++)
                getItems().add(Integer.valueOf(i));
        }
    }

    @SuppressWarnings("initialization")
    public Table(View parent, RecordSet rs)
    {
        super(new TableDisplay(rs));
        Pane spacer = new Pane();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button addButton = new Button("+");
        addButton.setOnAction(e -> {
            try
            {
                parent.add(new Table(parent, SummaryStatistics.guiCreate(rs).getResult()));
            }
            catch (Exception ex)
            {
                ex.printStackTrace();
                // TODO tell user
            }
        });

        setTop(new HBox(new Label(rs.getTitle()), spacer, addButton));
    }
}
