package records.gui;

import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.DataSource;
import records.data.EditableRecordSet;
import records.data.ImmediateDataSource;
import records.data.TableManager;
import records.error.InternalException;
import records.error.UserException;
import records.gui.grid.VirtualGrid;
import records.importers.manager.ImporterManager;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Workers;
import utility.Workers.Priority;
import utility.gui.FXUtility;
import utility.gui.GUI;
import utility.gui.TranslationUtility;

import java.io.File;
import java.net.MalformedURLException;
import java.util.HashMap;
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
        
        @OnThread(Tag.FXPlatform)
        public TableManager _test_getTableManager();

        @OnThread(Tag.FXPlatform)
        public VirtualGrid _test_getVirtualGrid();
    }

    // If src is null, make new
    public static MainWindowActions show(final Stage stage, File destinationFile, @Nullable Pair<File, String> src) throws UserException, InternalException
    {
        Label emptyMessage = new Label(TranslationUtility.getString("main.emptyHint"));
        emptyMessage.getStyleClass().add("main-empty-hint");
        emptyMessage.setWrapText(true);
        emptyMessage.setMaxWidth(400.0);

        View v = new View(destinationFile, emptyMessage::setVisible);
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
            /*
            GUI.menu("menu.data",
                GUI.menuItem("menu.data.new", () -> newTable(v)),
                GUI.menuItem("menu.data.import.file", () -> chooseAndImportFile(v, stage)),
                GUI.menuItem("menu.data.import.link", () -> chooseAndImportURL(v, stage))
            ),
            */
            GUI.menu("menu.view",
                GUI.menuItem("menu.view.find", () -> v.new FindEverywhereDialog().showAndWait()),
                GUI.menuItem("menu.view.tasks", () -> TaskManagerWindow.getInstance().show())
            )
        );

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
                DataSource rs = HTMLImporter.importHTMLFile(v.getManager(), new File("S:\\Downloads\\Report_10112016.xls")).get(0);
                    //TextImporter.importTextFile(new File("J:\\price\\farm-output-jun-2016.txt"  "J:\\price\\detailed.txt"));
                Platform.runLater(() -> Utility.alertOnErrorFX_(() -> v.addSource(rs)));
            }
            catch (IOException | InternalException | UserException ex)
            {
                showError(ex);
            }
        });
        */

        BorderPane root = new BorderPane(new StackPane(v, emptyMessage), menuBar, null, null, null);
        Scene scene = new Scene(root);
        scene.getStylesheets().addAll(FXUtility.getSceneStylesheets("mainview.css"));
        stage.setScene(scene);
        stage.setWidth(1000);
        stage.setHeight(800);

        if (src != null)
        {
            @NonNull Pair<File, String> srcFinal = src;
            Workers.onWorkerThread("Load", Priority.LOAD_FROM_DISK, () -> FXUtility.alertOnError_(err -> TranslationUtility.getString("error.loading", srcFinal.getFirst().getAbsolutePath(), err), () -> v.getManager().loadAll(srcFinal.getSecond())));
        }

        stage.show();
        //org.scenicview.ScenicView.show(stage.getScene());
        return new MainWindowActions()
        {
            @Override
            public void importFile(File file)
            {
                try
                {
                    ImporterManager.getInstance().importFile(stage, v.getManager(), file, file.toURI().toURL(), ds -> recordTable(v, ds));
                }
                catch (MalformedURLException e)
                {
                    FXUtility.logAndShowError("error.filePath", e);
                }
            }

            @Override
            public @OnThread(Tag.FXPlatform) TableManager _test_getTableManager()
            {
                return v.getManager();
            }

            @Override
            public @OnThread(Tag.FXPlatform) VirtualGrid _test_getVirtualGrid()
            {
                return v.getGrid();
            }
        };
    }

    private static void chooseAndImportFile(View v, Stage stage)
    {
        ImporterManager.getInstance().chooseAndImportFile(stage, v.getManager(), ds -> recordTable(v, ds));
    }

    private static void chooseAndImportURL(View v, Stage stage)
    {
        ImporterManager.getInstance().chooseAndImportURL(stage, v.getManager(), ds -> recordTable(v, ds));
    }

    @OnThread(Tag.Any)
    private static void recordTable(View v, DataSource ds)
    {
        Workers.onWorkerThread("Registering table", Priority.SAVE_ENTRY, () -> v.getManager().record(ds));
    }

    public static void closeAll()
    {
        // Take copy to avoid concurrent modification:
        new HashMap<>(views).forEach((v, s) -> {
            v.ensureSaved();
            s.hide();
        });
    }

    /*
    private static void newTable(View v)
    {
        Workers.onWorkerThread("Create new table", Priority.SAVE_ENTRY, () -> {
            try
            {
                EditableRecordSet newRecordSet = EditableRecordSet.newRecordSetSingleColumn();
                ImmediateDataSource ds = new ImmediateDataSource(v.getManager(), newRecordSet);
                v.getManager().record(ds);
            }
            catch (InternalException | UserException ex)
            {
                FXUtility.logAndShowError("newtable.error", ex);
            }
        });
    }
    */

    public static Map<View, Stage> _test_getViews()
    {
        return views;
    }
}
