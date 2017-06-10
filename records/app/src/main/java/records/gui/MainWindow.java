package records.gui;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Stage;
import org.apache.commons.io.FileUtils;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.DataSource;
import records.data.EditableRecordSet;
import records.data.ImmediateDataSource;
import records.error.InternalException;
import records.error.UserException;
import records.importers.HTMLImport;
import records.importers.TextImport;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;
import utility.Workers;
import utility.Workers.Priority;
import utility.gui.FXUtility;
import utility.gui.GUI;
import utility.gui.TranslationUtility;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Created by neil on 17/04/2017.
 */
@OnThread(Tag.FXPlatform)
public class MainWindow
{
    private final static IdentityHashMap<View, Stage> views = new IdentityHashMap<>();

    public static interface MainWindowActions
    {
        @OnThread(Tag.FXPlatform)
        public void importFile(File file);
    }

    // If src is null, make new
    public static MainWindowActions show(final Stage stage, File destinationFile, @Nullable String src) throws UserException, InternalException
    {
        View v = new View(destinationFile);
        stage.titleProperty().bind(v.titleProperty());
        views.put(v, stage);
        stage.setOnHidden(e -> {
            views.remove(v);
        });

        MenuBar menuBar = new MenuBar(
            GUI.menu("menu.project",
                GUI.menuItem("menu.project.new", () -> InitialWindow.newProject(stage)),
                GUI.menuItem("menu.project.open", () -> InitialWindow.chooseAndOpenProject(stage)),
                new DummySaveMenuItem(v),
                GUI.menuItem("menu.project.saveAs", () -> {
                    FileChooser fc = new FileChooser();
                    File dest = fc.showSaveDialog(stage);
                    if (dest == null)
                        return;
                    v.setDiskFileAndSave(dest);
                }),
                GUI.menuItem("menu.project.close", () -> {stage.hide();}),
                GUI.menuItem("menu.exit", () -> {closeAll();})
            ),
            GUI.menu("menu.data",
                GUI.menuItem("menu.data.new", () -> newTable(v)),
                GUI.menuItem("menu.data.import", () -> importText(v, stage))
            )
        );

        Menu menu = new Menu("Data");

        MenuItem importItem = new MenuItem("Text");
        importItem.setOnAction(e -> {
            importText(v, stage);
        });
        MenuItem importHTMLItem = new MenuItem("HTML");
        importHTMLItem.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            File chosen = fc.showOpenDialog(stage);
            if (chosen != null)
            {
                @NonNull File chosenFinal = chosen;
                Workers.onWorkerThread("GuessFormat data", Priority.LOAD_FROM_DISK, () ->
                {
                    try
                    {
                        for (DataSource rs : HTMLImport.importHTMLFile(v.getManager(), chosenFinal))
                        {
                            Platform.runLater(() -> Utility.alertOnErrorFX_(() -> v.addSource(rs)));
                        }
                    }
                    catch (IOException | InternalException | UserException ex)
                    {
                        FXUtility.logAndShowError("import.html.error", ex);
                    }
                });
            }
        });
        menu.getItems().addAll(importItem, importHTMLItem);

        MenuItem openItem = new MenuItem(TranslationUtility.getString("menu.project.open"));
        openItem.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            File open = fc.showOpenDialog(stage);
            if (open != null)
            {
                try
                {
                    MainWindow.show(new Stage(), open, FileUtils.readFileToString(open, "UTF-8"));
                }
                catch (IOException | UserException | InternalException ex)
                {
                    FXUtility.logAndShowError("open.error", ex);
                }
            }
        });
        menu.getItems().add(openItem);

        /*
        MenuItem saveItem = new MenuItem("Save to Clipboard");
        saveItem.setOnAction(e -> {
            v.save(null, s ->
                    Platform.runLater(() -> Clipboard.getSystemClipboard().setContent(Collections.singletonMap(DataFormat.PLAIN_TEXT, s))));
        });
        menu.getItems().add(saveItem);
        */
        /*
        Workers.onWorkerThread("Example import", () -> {
            try
            {
                DataSource rs = HTMLImport.importHTMLFile(v.getManager(), new File("S:\\Downloads\\Report_10112016.xls")).get(0);
                    //TextImport.importTextFile(new File("J:\\price\\farm-output-jun-2016.txt"  "J:\\price\\detailed.txt"));
                Platform.runLater(() -> Utility.alertOnErrorFX_(() -> v.addSource(rs)));
            }
            catch (IOException | InternalException | UserException ex)
            {
                showError(ex);
            }
        });
        */

        ScrollPane scrollPane = new ScrollPane(v);

        // From https://reportmill.wordpress.com/2014/06/03/make-scrollpane-content-fill-viewport-bounds/
        FXUtility.addChangeListenerPlatform(scrollPane.viewportBoundsProperty(), bounds -> {
            if (bounds != null)
            {
                Node content = scrollPane.getContent();
                scrollPane.setFitToWidth(content.prefWidth(-1) < bounds.getWidth());
                scrollPane.setFitToHeight(content.prefHeight(-1) < bounds.getHeight());
            }
        });

        BorderPane root = new BorderPane(scrollPane, menuBar, null, null, null);
        Scene scene = new Scene(root);
        scene.getStylesheets().addAll(FXUtility.getSceneStylesheets("mainview.css"));
        stage.setScene(scene);
        stage.setWidth(1000);
        stage.setHeight(800);

        if (src != null)
        {
            @NonNull String srcFinal = src;
            Workers.onWorkerThread("Load", Priority.LOAD_FROM_DISK, () -> Utility.alertOnError_(() -> v.getManager().loadAll(srcFinal)));
        }

        stage.show();
        org.scenicview.ScenicView.show(stage.getScene());
        return new MainWindowActions()
        {
            @Override
            public void importFile(File file)
            {
                importText(v, file);
            }
        };
    }

    private static void importText(View v, Stage stage)
    {
        @Nullable File chosen = FXUtility.chooseFileOpen("data.import.dialogTitle", "dataOpen", stage,
            new ExtensionFilter(TranslationUtility.getString("data.import.type.text"), "*.txt", "*.csv"),
            new ExtensionFilter(TranslationUtility.getString("extension.allfiles"), "*.*")
        );
        if (chosen != null)
        {
            importText(v, chosen);
        }
    }

    private static void importText(View v, @NonNull File chosenFinal)
    {
        Workers.onWorkerThread("GuessFormat data", Priority.LOAD_FROM_DISK, () ->
        {
            try
            {
                TextImport.importTextFile(v.getManager(), chosenFinal, rs ->
                        Utility.alertOnErrorFX_(() -> v.addSource(rs)));
            }
            catch (InternalException | UserException | IOException ex)
            {
                FXUtility.logAndShowError("import.text.error", ex);
            }
        });
    }

    public static void closeAll()
    {
        views.forEach((v, s) -> {
            v.ensureSaved();
            s.hide();
        });
    }

    private static void newTable(View v)
    {
        Workers.onWorkerThread("Create new table", Priority.SAVE_ENTRY, () -> {
            try
            {
                EditableRecordSet rs = new EditableRecordSet(Collections.emptyList(), () -> 0);
                ImmediateDataSource ds = new ImmediateDataSource(v.getManager(), rs);
            }
            catch (InternalException | UserException ex)
            {
                FXUtility.logAndShowError("newtable.error", ex);
            }
        });
    }

    private static class DummySaveMenuItem extends MenuItem
    {
        private final ObjectProperty<Object> dummyNowBinding = new SimpleObjectProperty<>(new Object());
        private final @OnThread(Tag.FXPlatform) StringBinding text;

        @OnThread(Tag.FXPlatform)
        public DummySaveMenuItem(View view)
        {
            setDisable(true);
            text = Bindings.createStringBinding(() ->
            {
                @Nullable Instant lastSave = view.lastSaveTime().get();
                if (lastSave == null)
                    return TranslationUtility.getString("menu.project.modified");
                else
                    return "" + (Instant.now().getEpochSecond() - lastSave.getEpochSecond());
            }, view.lastSaveTime(), dummyNowBinding);
            // Invalidating this binding on show will force re-evaluation of the time gap:
            FXUtility.onceNotNull(parentMenuProperty(), menu -> menu.addEventHandler(Menu.ON_SHOWING, e -> {
                text.invalidate();
            }));
            textProperty().bind(TranslationUtility.bindString("menu.project.save", text));
        }


    }

    public static Map<View, Stage> _test_getViews()
    {
        return views;
    }
}
