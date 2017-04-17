package records.gui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Node;
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
import org.apache.commons.io.FileUtils;
import records.data.DataSource;
import records.data.EditableRecordSet;
import records.data.ImmediateDataSource;
import records.error.InternalException;
import records.error.UserException;
import records.importers.HTMLImport;
import records.importers.TextImport;
import records.transformations.TransformationEditor;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;
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
        new MainWindow(primaryStage, null);
    }


    // TODO pass -XX:AutoBoxCacheMax= parameter on execution
    public static void main(String[] args)
    {
        Application.launch(Main.class);
    }
}
