package records.gui;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import org.checkerframework.checker.guieffect.qual.UIEffect;
import records.data.RecordSet;
import records.data.SummaryStatistics;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;
import utility.Workers;

import java.util.ArrayList;

/**
 * Created by neil on 18/10/2016.
 */
@OnThread(Tag.FXPlatform)
public class Table extends BorderPane
{
    private static final int INITIAL_LOAD = 100;
    private static final int LOAD_CHUNK = 100;

    @OnThread(Tag.FXPlatform)
    private static class TableDisplay extends TableView<Integer>
    {
        @SuppressWarnings("initialization")
        @UIEffect
        public TableDisplay(RecordSet recordSet)
        {
            super();
            getColumns().setAll(recordSet.getDisplayColumns());
            Workers.onWorkerThread("Determining row count for " + recordSet.getTitle(), () -> {
                ArrayList<Integer> indexesToAdd = new ArrayList<Integer>();
                Utility.alertOnError_(() -> {
                    for (int i = 0; i < INITIAL_LOAD; i++)
                        if (recordSet.indexValid(i))
                            indexesToAdd.add(Integer.valueOf(i));

                });
                // TODO when user causes a row to be shown, load LOAD_CHUNK entries
                // afterwards.
                Platform.runLater(() -> getItems().addAll(indexesToAdd));
            });

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
                SummaryStatistics.withGUICreate(rs, r -> parent.add(new Table(parent, r.getResult())));
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
