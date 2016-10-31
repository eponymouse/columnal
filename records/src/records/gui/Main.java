package records.gui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import records.importers.TextImport;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Workers;

import java.io.File;
import java.io.IOException;

/**
 * Created by neil on 18/10/2016.
 */
public class Main extends Application
{
    @Override
    @OnThread(value = Tag.FXPlatform,ignoreParent = true)
    public void start(final Stage primaryStage) throws Exception
    {
        View v = new View();
        Menu menu = new Menu("Data");
        MenuItem importItem = new MenuItem("GuessFormat");
        importItem.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            File chosen = fc.showOpenDialog(primaryStage);
            if (chosen != null)
            {
                Workers.onWorkerThread("GuessFormat data", () ->
                {
                    try
                    {
                        TextImport.importTextFile(chosen, rs ->
                            Platform.runLater(() -> v.add(new Table(v, rs))));
                    }
                    catch (IOException ex)
                    {
                        ex.printStackTrace();
                        Platform.runLater(() -> new Alert(AlertType.ERROR, ex.getMessage() == null ? "" : ex.getMessage(), ButtonType.OK).showAndWait());
                    }
                });
            }
        });
        menu.getItems().add(importItem);
        Workers.onWorkerThread("Example import", () -> {
            try
            {
                TextImport.importTextFile(new File(/*"J:\\price\\farm-output-jun-2016.txt"*/"J:\\price\\detailed.txt"), rs ->
                Platform.runLater(() -> v.add(new Table(v, rs))));
            }
            catch (IOException ex)
            {
            }
        });

        BorderPane root = new BorderPane(new ScrollPane(v), new MenuBar(menu), null, null, null);
        primaryStage.setScene(new Scene(root));
        primaryStage.setWidth(800);
        primaryStage.setHeight(600);
        primaryStage.show();
    }

    // TODO pass -XX:AutoBoxCacheMax= parameter on execution
    public static void main(String[] args)
    {
        Application.launch(Main.class);
    }
}
