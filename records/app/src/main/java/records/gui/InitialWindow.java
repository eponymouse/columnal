package records.gui;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
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
                    if (chooseAndOpenProject(stage))
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
            if (chooseAndOpenProject(stage))
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
        mruListView.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.SPACE)
            {
                @Nullable File selected = mruListView.getSelectionModel().getSelectedItem();
                if (selected != null)
                {
                    openProject(stage, selected);
                }
            }
        });
        
        mruListView.getItems().setAll(Utility.readRecentFilesList());
        Label titleLabel = GUI.label("us", "initial-title");
        ImageView logo = TranslationUtility.makeImageView("logo.png");
        if (logo != null)
        {
            logo.setPreserveRatio(true);
            logo.setSmooth(true);
            logo.fitHeightProperty().bind(titleLabel.heightProperty().multiply(0.7));
            @NonNull ImageView logoFinal = logo;
            // Problem is that the imageView is too large initially because it shows at original size
            // and only sizes down once the label has been sized, but by then it's too late and the window
            // sizing is ruined.  So we only add the logo once the label height is set right:
            titleLabel.heightProperty().addListener(new ChangeListener<Number>()
            {
                @Override
                public void changed(ObservableValue<? extends Number> prop, Number oldVal, Number newVal)
                {
                    if (newVal.doubleValue() >= 1.0)
                    {
                        titleLabel.setGraphic(logoFinal);
                        titleLabel.heightProperty().removeListener(this);
                    }
                }
            });
        }
        VBox content = GUI.vbox("initial-content",
                titleLabel,
                headed("initial-section-new", GUI.label("initial.new.title", "initial-heading"), newButton, GUI.labelWrap("initial.new.detail")),
                headed("initial-section-open", GUI.label("initial.open.title", "initial-heading"), openButton, GUI.vbox("initial-recent", GUI.label("initial.open.recent", "initial-subheading"), mruListView))
        );
        Scene scene = new Scene(new BorderPane(content, menuBar, null, null, null));
        scene.getStylesheets().addAll(FXUtility.getSceneStylesheets("initial"));
        stage.setScene(scene);
        stage.setTitle(TranslationUtility.getString("us"));
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

    // Returns true if successfully opened a project
    public static boolean chooseAndOpenProject(Stage parent)
    {
        File src = FXUtility.chooseFileOpen("project.open.dialogTitle", "projectOpen", parent, FXUtility.getProjectExtensionFilter(Main.EXTENSION_INCL_DOT));
        if (src != null)
        {
            return openProject(parent, src);
        }
        return false;
    }

    // Returns true if successfully opened a project
    private static boolean openProject(Stage parent, File src)
    {
        try
        {
            MainWindow.show(new Stage(), src, new Pair<>(src, FileUtils.readFileToString(src, "UTF-8")));
            Utility.usedFile(src);
            // Only hide us if the load and show completed successfully:
            parent.hide();
            return true;
        }
        catch (IOException | InternalException | UserException ex)
        {
            FXUtility.logAndShowError("error.readingfile", ex);
            return false;
        }
    }

    @OnThread(Tag.FXPlatform)
    public static MainWindow.@Nullable MainWindowActions newProject(@Nullable Stage parent)
    {
        return FXUtility.<MainWindow.@Nullable MainWindowActions>alertOnErrorFX("Error creating new file", () ->
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
