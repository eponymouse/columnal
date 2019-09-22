package records.gui;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.CellPosition;
import records.data.DataSource;
import records.data.TableManager;
import records.error.InternalException;
import records.error.UserException;
import records.gui.Main.UpgradeInfo;
import records.gui.grid.VirtualGrid;
import records.importers.manager.ImporterManager;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Workers;
import utility.Workers.Priority;
import utility.gui.FXUtility;
import utility.gui.GUI;
import utility.TranslationUtility;

import java.io.File;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

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
        
        @OnThread(Tag.Any)
        public TableManager _test_getTableManager();

        @OnThread(Tag.Any)
        public VirtualGrid _test_getVirtualGrid();

        @OnThread(Tag.FXPlatform)
        public DataCellSupplier.@Nullable VersionedSTF _test_getDataCell(CellPosition position);
        
        // What file are we saving to in this main window?
        @OnThread(Tag.FXPlatform)
        public File _test_getCurFile();

        public int _test_getSaveCount();
    }

    // If src is null, make new
    public static MainWindowActions show(final Stage stage, File destinationFile, @Nullable Pair<File, String> src, @Nullable CompletionStage<Optional<UpgradeInfo>> upgradeInfo) throws UserException, InternalException
    {
        View v = new View(destinationFile);
        // We don't use bind because FXUtility.setIcon needs to temporarily change title:
        v.addTitleListenerAndCallNow(stage::setTitle);
        views.put(v, stage);
        stage.setOnHidden(e -> {
            views.remove(v);
        });

        MenuBar menuBar = new MenuBar(
            GUI.menu("menu.project",
                GUI.menuItem("menu.project.new", () -> InitialWindow.newProject(stage, null)),
                GUI.menuItem("menu.project.open", () -> InitialWindow.chooseAndOpenProject(stage, null)),
                GUI.menu("menu.project.open.recent", InitialWindow.makeRecentProjectMenu(stage, upgradeInfo).toArray(new MenuItem[0])),
                new SaveMenuItem(v),
                GUI.menuItem("menu.project.saveAs", () -> {
                    FileChooser fc = new FileChooser();
                    fc.getExtensionFilters().addAll(FXUtility.getProjectExtensionFilter(Main.EXTENSION_INCL_DOT));
                    File dest = fc.showSaveDialog(stage);
                    if (dest == null)
                        return;
                    v.setDiskFileAndSave(dest);
                }),
                GUI.menuItem("menu.project.saveCopy", () -> {
                    FileChooser fc = new FileChooser();
                    File dest = fc.showSaveDialog(stage);
                    fc.getExtensionFilters().addAll(FXUtility.getProjectExtensionFilter(Main.EXTENSION_INCL_DOT));
                    if (dest == null)
                        return;
                    v.doSaveTo(false, dest);
                }),
                GUI.menuItem("menu.project.close", () -> {stage.hide();}),
                GUI.menuItem("menu.exit", () -> {closeAll();})
            ),
            GUI.menu("menu.edit",
                GUI.menuItem("menu.edit.undo", () -> {v.undo();})
            ),
            GUI.menu("menu.view",
                // TODO finish the find dialog
                //GUI.menuItem("menu.view.find", () -> v.new FindEverywhereDialog().showAndWait()),
                GUI.menuItem("menu.view.goto.row", () -> v.gotoRowDialog()),
                new SeparatorMenuItem(),
                GUI.menuItem("menu.view.types", () -> {
                    new TypesDialog(v, v.getManager().getTypeManager()).showAndWait();
                    v.modified();
                }),
                GUI.menuItem("menu.view.units", () -> {
                    new UnitsDialog(v, v.getManager().getTypeManager()).showAndWaitNested();
                    v.modified();
                }),
                GUI.menuItem("menu.view.tasks", () -> TaskManagerWindow.getInstance().show())
            ),
            GUI.menu("menu.help",
                GUI.menuItem("menu.help.about", () -> new AboutDialog(v).showAndWait())
            )
        );
        menuBar.setUseSystemMenuBar(true);

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
        
        TextFlow banner = new TextFlow() {
            @Override
            protected double computeMaxHeight(double width)
            {
                return super.computePrefHeight(width);
            }
        };
        banner.getStyleClass().add("banner-message");
        StackPane.setAlignment(banner, Pos.TOP_CENTER);
        banner.setVisible(false);
        updateBanner(v, banner, true);

        StackPane stackPane = new StackPane(v, banner);
        if (upgradeInfo != null)
        {
            upgradeInfo.thenAccept(new Consumer<Optional<UpgradeInfo>>()
            {
                @Override
                @OnThread(value = Tag.Worker, ignoreParent = true)
                public void accept(Optional<UpgradeInfo> opt)
                {
                    opt.ifPresent(u -> Platform.runLater(() -> u.showIn(stackPane, 1)));
                }
            });
        }
            
        BorderPane root = new BorderPane(stackPane, menuBar, null, null, null);
        Scene scene = new Scene(root);
        scene.getStylesheets().addAll(FXUtility.getSceneStylesheets("mainview.css"));
        stage.setScene(scene);
        stage.setWidth(1000);
        stage.setHeight(800);
        FXUtility.setIcon(stage);

        if (src != null)
        {
            @NonNull Pair<File, String> srcFinal = src;
            Workers.onWorkerThread("Load", Priority.LOAD_FROM_DISK, () -> FXUtility.alertOnError_("Error loading " + srcFinal.getFirst().getName(), err -> {
                Platform.runLater(() -> updateBanner(v, banner, false));
                return TranslationUtility.getString("error.loading", srcFinal.getFirst().getAbsolutePath(), err);
            }, () -> {
                v.getManager().loadAll(srcFinal.getSecond(), v::loadColumnWidths);
                Platform.runLater(() -> {
                    v.enableWriting();
                    updateBanner(v, banner, false);
                });
            }));
        }
        else
        {
            // Enable writing mode
            v.enableWriting();
            updateBanner(v, banner, false);
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
                    ImporterManager.getInstance().importFile(stage, v.getManager(), CellPosition.ORIGIN.offsetByRowCols(1, 1), file, file.toURI().toURL(), ds -> recordTable(v, ds));
                }
                catch (MalformedURLException e)
                {
                    FXUtility.logAndShowError("error.filePath", e);
                }
            }

            @Override
            @OnThread(Tag.Any)
            public TableManager _test_getTableManager()
            {
                return v.getManager();
            }

            @Override
            @OnThread(Tag.Any)
            public VirtualGrid _test_getVirtualGrid()
            {
                return v.getGrid();
            }

            @Override
            @OnThread(Tag.FXPlatform)
            public DataCellSupplier.@Nullable VersionedSTF _test_getDataCell(CellPosition position)
            {
                return v.getDataCellSupplier()._test_getCellAt(position);
            }

            @Override
            @OnThread(Tag.FXPlatform)
            public File _test_getCurFile()
            {
                return v.getSaveFile();
            }

            @Override
            @OnThread(Tag.FXPlatform)
            public int _test_getSaveCount()
            {
                return v.test_getSaveCount();
            }
        };
    }

    private static void updateBanner(View v, TextFlow banner, boolean loading)
    {
        if (loading)
        {
            Text text = new Text(
                "Loading..."
            );
            text.getStyleClass().add("banner-message-text");
            FXUtility.setPseudoclass(banner, "error", false);
            banner.getChildren().setAll(text);
            banner.setVisible(true);
        }
        else if (v.isReadOnly())
        {
            Text text = new Text(
                "This file is open for reading only, due to a loading error.  Changes will not be saved."
            );
            text.getStyleClass().add("banner-message-text");
            FXUtility.setPseudoclass(banner, "error", true);
            banner.getChildren().setAll(text);
            banner.setVisible(true);
        }
        else
        {
            FXUtility.setPseudoclass(banner, "error", false);
            banner.getChildren().clear();
            banner.setVisible(false);
        }
    }

    /*
        private static void chooseAndImportFile(View v, Stage stage)
        {
            ImporterManager.getInstance().chooseAndImportFile(stage, v.getManager(), ds -> recordTable(v, ds));
        }
    
        private static void chooseAndImportURL(View v, Stage stage)
        {
            ImporterManager.getInstance().chooseAndImportURL(stage, v.getManager(), ds -> recordTable(v, ds));
        }
    */
    @OnThread(Tag.Any)
    private static void recordTable(View v, DataSource ds)
    {
        Workers.onWorkerThread("Registering table", Priority.SAVE, () -> v.getManager().record(ds));
    }

    public static void closeAll()
    {
        // Take copy to avoid concurrent modification:
        new HashMap<View, Stage>(views).forEach((v, s) -> {
            v.ensureSaved();
            s.hide();
        });
    }

    public static Map<View, Stage> _test_getViews()
    {
        return views;
    }
}
