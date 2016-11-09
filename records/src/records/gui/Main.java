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
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import records.data.DataSource;
import records.data.RecordSet;
import records.error.InternalException;
import records.error.UserException;
import records.importers.HTMLImport;
import records.importers.TextImport;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Workers;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

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
                        DataSource rs = TextImport.importTextFile(chosen);
                        Platform.runLater(() -> v.add(rs, null));
                    }
                    catch (IOException | InternalException | UserException ex)
                    {
                        ex.printStackTrace();
                        Platform.runLater(() -> new Alert(AlertType.ERROR, ex.getMessage() == null ? "" : ex.getMessage(), ButtonType.OK).showAndWait());
                    }
                });
            }
        });
        menu.getItems().add(importItem);
        MenuItem saveItem = new MenuItem("Save to Clipboard");
        saveItem.setOnAction(e -> {
            v.save(null, s ->
                Clipboard.getSystemClipboard().setContent(Collections.singletonMap(DataFormat.PLAIN_TEXT, s)));
        });
        menu.getItems().add(saveItem);
        Workers.onWorkerThread("Example import", () -> {
            try
            {
                DataSource rs = HTMLImport.importHTMLFile(new File("S:\\Downloads\\Report_28112014.xls")).get(0);
                    //TextImport.importTextFile(new File(/*"J:\\price\\farm-output-jun-2016.txt"*/"J:\\price\\detailed.txt"));
                Platform.runLater(() -> v.add(rs, null));
            }
            catch (IOException | InternalException | UserException ex)
            {
                ex.printStackTrace();
            }
        });

        BorderPane root = new BorderPane(new ScrollPane(v), new MenuBar(menu), null, null, null);
        Scene scene = new Scene(root);
        scene.getStylesheets().add("file:records/css/mainview.css");
        primaryStage.setScene(scene);
        primaryStage.setWidth(1000);
        primaryStage.setHeight(800);
        primaryStage.show();
    }

    // TODO pass -XX:AutoBoxCacheMax= parameter on execution
    public static void main(String[] args)
    {
        Application.launch(Main.class);
    }
}
