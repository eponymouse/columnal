package records.gui;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.commons.io.FileUtils;
import org.checkerframework.checker.nullness.qual.NonNull;
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
import java.util.Arrays;

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
        mruListView.setPrefHeight(200.0);
        mruListView.setMaxHeight(Double.MAX_VALUE);
        mruListView.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2)
            {
                @Nullable File selected = mruListView.getSelectionModel().getSelectedItem();
                if (selected != null)
                {
                    openProject(stage, selected);
                }
            }
        });
        mruListView.getItems().setAll(Utility.readRecentFilesList());
        VBox content = GUI.vbox("initial-content",
                headed("initial-section-new", GUI.label("initial.new.title", "initial-heading"), newButton, GUI.labelWrap("initial.new.detail")),
                headed("initial-section-open", GUI.label("initial.open.title", "initial-heading"), openButton, GUI.vbox("initial-recent", GUI.label("initial.open.recent", "initial-subheading"), mruListView))
        );
        Scene scene = new Scene(new BorderPane(content, menuBar, null, null, null));
        scene.getStylesheets().addAll(FXUtility.getSceneStylesheets("initial"));
        stage.setScene(scene);
        stage.show();
        //org.scenicview.ScenicView.show(scene);
    }

    private static Node headed(String style, Label header, Node... content)
    {
        Node[] all = new Node[content.length + 1];
        all[0] = header;
        for (int i = 0; i < content.length; i++)
        {
            VBox.setMargin(content[i], new Insets(0, 0, 0, 30));
            all[i + 1] = content[i];
        }
        return GUI.vbox(style, all);
    }

    public static void chooseAndOpenProject(Stage parent)
    {
        File src = FXUtility.chooseFileOpen("project.open.dialogTitle", "projectOpen", parent, FXUtility.getProjectExtensionFilter());
        if (src != null)
        {
            openProject(parent, src);
        }
    }

    private static void openProject(Stage parent, File src)
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

    @OnThread(Tag.FXPlatform)
    public static MainWindow.@Nullable MainWindowActions newProject(@Nullable Stage parent)
    {
        return FXUtility.<MainWindow.@Nullable MainWindowActions>alertOnErrorFX(() ->
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
