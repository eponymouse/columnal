package records.gui;

import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.commons.io.FileUtils;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
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
        MenuBar menuBar = new MenuBar(
            GUI.menu("menu.project",
                GUI.menuItem("menu.project.new", () -> {
                    newProject(stage);
                    stage.hide();
                }),
                GUI.menuItem("menu.project.open", () -> {
                    chooseAndOpenProject(stage);
                    stage.hide();
                }),
                GUI.menuItem("menu.exit", () -> {
                    MainWindow.closeAll();
                    stage.hide();
                })
            )
        );
        menuBar.setUseSystemMenuBar(true);
        Button newButton = GUI.button("initial.new", () -> {
            newProject(stage);
            stage.hide();
        });
        Button openButton = GUI.button("initial.open", () -> {
            chooseAndOpenProject(stage);
            stage.hide();
        });
        ListView<File> mruListView = new ListView<>();
        mruListView.setItems(Utility.getRecentFilesList());
        VBox content = GUI.vbox("initial-content",
                GUI.vbox("initial-section-new", GUI.label("initial.new.title", "initial-heading"), newButton, GUI.labelWrap("initial.new.detail")),
                GUI.vbox("initial-section-open", GUI.label("initial.open.title", "initial-heading"), openButton, GUI.label("initial.open.recent"), mruListView)
        );
        Scene scene = new Scene(new BorderPane(content, menuBar, null, null, null));
        scene.getStylesheets().addAll(FXUtility.getSceneStylesheets("initial"));
        stage.setScene(scene);
        stage.show();
        //org.scenicview.ScenicView.show(scene);
    }

    public static void chooseAndOpenProject(Stage parent)
    {
        File src = FXUtility.chooseFileOpen("project.open.dialogTitle", "projectOpen", parent, FXUtility.getProjectExtensionFilter());
        if (src != null)
        {
            try
            {
                MainWindow.show(new Stage(), src, new Pair<>(src, FileUtils.readFileToString(src, "UTF-8")));
                Utility.usedFile(src);
                // Only hide us if the load and show completed successfully:
                parent.hide();
            }
            catch (IOException | InternalException | UserException ex)
            {
                FXUtility.logAndShowError("error.readingfile", ex);
            }
        }
    }

    @OnThread(Tag.FXPlatform)
    public static MainWindow.@Nullable MainWindowActions newProject(@Nullable Stage parent)
    {
        return Utility.<MainWindow.@Nullable MainWindowActions>alertOnErrorFX(() ->
        {
            @Nullable File dest;
            try
            {
                dest = Utility.getNewAutoSaveFile();
            }
            catch (IOException e)
            {
                FXUtility.logAndShowError("new.autosave.error", e);
                dest = new FileChooser().showSaveDialog(parent);
            }
            if (dest != null)
                return MainWindow.show(new Stage(), dest, null);
            else
                return null;
        });
    }
}
