package records.gui;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.commons.io.FileUtils;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;
import utility.gui.FXUtility;
import utility.gui.HButton;
import utility.gui.HLabel;
import utility.gui.TranslationUtility;

import java.io.File;
import java.io.IOException;

/**
 * Created by neil on 17/04/2017.
 */
@OnThread(Tag.FXPlatform)
public class InitialWindow
{
    public static void show(Stage stage)
    {
        VBox content = new VBox();
        //TODO menu bar at the top
        Button newButton = new HButton("initial.new", () -> Utility.alertOnErrorFX_(() -> MainWindow.show(null)));
        Button openButton = new HButton("initial.open", () -> {
            File src = new FileChooser().showOpenDialog(stage);
            if (src != null)
            {
                try
                {
                    MainWindow.show(FileUtils.readFileToString(src, "UTF-8"));
                    // Only hide us if the load and show completed successfully:
                    stage.hide();
                }
                catch (IOException | InternalException | UserException ex)
                {
                    FXUtility.logAndShowErrorFX("error.readingfile", ex);
                }
            }
        });
        content.getChildren().addAll(
                new VBox(new HLabel("initial.new.title", "initial.heading"), newButton, new Label("From there you can import existing data (CSV files, etc)")),
                new VBox(new HLabel("initial.open.title", "initial.heading"), openButton, new ListView<>())
        );
        stage.setScene(new Scene(content));
        stage.show();
    }
}
