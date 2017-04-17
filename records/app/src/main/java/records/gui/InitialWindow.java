package records.gui;

import javafx.scene.Scene;
import javafx.scene.control.*;
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
import utility.gui.*;

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
        MenuBar menuBar = new MenuBar(
            GUI.menu("menu.project",
                GUI.menuItem("menu.project.new", () -> newProject()),
                GUI.menuItem("menu.project.open", () -> chooseAndOpenProject(stage))
            )
        );
        menuBar.setUseSystemMenuBar(true);
        Button newButton = GUI.button("initial.new", () -> newProject());
        Button openButton = GUI.button("initial.open", () -> chooseAndOpenProject(stage));
        content.getChildren().addAll(
                menuBar,
                new VBox(GUI.label("initial.new.title", "initial.heading"), newButton, new Label("From there you can import existing data (CSV files, etc)")),
                new VBox(GUI.label("initial.open.title", "initial.heading"), openButton, new ListView<>())
        );
        stage.setScene(new Scene(content));
        stage.show();
    }

    private static void chooseAndOpenProject(Stage stage)
    {
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
    }

    @OnThread(Tag.FXPlatform)
    private static void newProject()
    {
        Utility.alertOnErrorFX_(() -> MainWindow.show(null));
    }
}
