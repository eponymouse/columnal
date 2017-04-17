package records.gui;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.gui.TranslationUtility;

/**
 * Created by neil on 17/04/2017.
 */
@OnThread(Tag.FXPlatform)
public class InitialWindow
{
    public static void show(Stage stage)
    {
        BorderPane content = new BorderPane();
        //TODO menu bar at the top
        Button newButton = new Button(TranslationUtility.getString("initial.new"));
        Button openButton = new Button(TranslationUtility.getString("initial.open"));
        TilePane tilePane = new TilePane(
                new VBox(new Label("Create New"), newButton, new Label("From there you can import existing data (CSV files, etc)")),
                new VBox(new Label("Open Existing"), openButton, new ListView<>())
        );
        tilePane.setPrefColumns(2);
        content.setCenter(tilePane);
        stage.setScene(new Scene(content));
        stage.show();
    }
}
