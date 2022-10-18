/*
 * Columnal: Safer, smoother data table processing.
 * Copyright (c) Neil Brown, 2016-2020, 2022.
 *
 * This file is part of Columnal.
 *
 * Columnal is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Columnal is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for 
 * more details.
 *
 * You should have received a copy of the GNU General Public License along 
 * with Columnal. If not, see <https://www.gnu.org/licenses/>.
 */

package xyz.columnal.gui;

import com.google.common.collect.ImmutableList;
import com.google.common.hash.Hashing;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.CellPosition;
import xyz.columnal.data.DataSource;
import xyz.columnal.data.Table.TableDisplayBase;
import xyz.columnal.data.TableManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.gui.CheckSummaryLabel.ChecksStateListener;
import xyz.columnal.gui.Main.UpgradeInfo;
import xyz.columnal.gui.grid.VirtualGrid;
import xyz.columnal.gui.table.HeadedDisplay;
import xyz.columnal.importers.manager.ImporterManager;
import xyz.columnal.transformations.Check;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.Workers;
import xyz.columnal.utility.Workers.Priority;
import xyz.columnal.utility.gui.FXUtility;
import xyz.columnal.utility.gui.GUI;
import xyz.columnal.utility.TranslationUtility;
import xyz.columnal.utility.gui.ScrollPaneFill;
import xyz.columnal.utility.gui.SmallDeleteButton;

import java.io.File;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
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
    private final BorderPane root;
    private final Stage stage;
    private final View v;
    private final CheckSummaryLabel checksLabel;
    
    private final ToggleGroup viewLeftToggleGroup = new ToggleGroup();
    private final RadioMenuItem viewLeftNone;
    private final RadioMenuItem viewLeftChecks;
    private LeftPaneType curLeftPaneType;

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
        MainWindow mainWindow = new MainWindow(stage, destinationFile, src, upgradeInfo);
        return mainWindow.getActions();
    }

    private MainWindow(final Stage stage, File destinationFile, @Nullable Pair<File, String> src, @Nullable CompletionStage<Optional<UpgradeInfo>> upgradeInfo) throws UserException, InternalException
    {
        this.stage = stage;
        v = new View(destinationFile);
        // We don't use bind because FXUtility.setIcon needs to temporarily change title:
        v.addTitleListenerAndCallNow(stage::setTitle);
        views.put(v, this.stage);
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
                        GUI.menuItem("menu.project.close", () -> {
                            stage.hide();
                        }),
                        GUI.menuItem("menu.exit", () -> {
                            closeAll();
                        })
                ),
                GUI.menu("menu.edit",
                        GUI.menuItem("menu.edit.undo", () -> {
                            v.undo();
                        }),
                        new SeparatorMenuItem(),
                        GUI.menuItem("menu.edit.settings", () -> {
                            v.editSettings();
                        })
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
                        GUI.menuItem("menu.view.tasks", () -> TaskManagerWindow.getInstance().show()),
                        new SeparatorMenuItem(),
                        this.viewLeftNone = GUI.radioMenuItem("menu.view.nopane", viewLeftToggleGroup),
                        this.viewLeftChecks = GUI.radioMenuItem("menu.view.checks", viewLeftToggleGroup)
                ),
                GUI.menu("menu.help",
                        GUI.menuItem("menu.help.about", () -> new AboutDialog(v).showAndWait())
                )
        );
        menuBar.setUseSystemMenuBar(true);

        curLeftPaneType = LeftPaneType.NONE;
        viewLeftToggleGroup.selectToggle(viewLeftNone);
        FXUtility.addChangeListenerPlatformNN(viewLeftToggleGroup.selectedToggleProperty(), l -> {
            if (l == viewLeftNone)
                FXUtility.mouse(this).showLeftPane(LeftPaneType.NONE);
            else if (l == viewLeftChecks)
                FXUtility.mouse(this).showLeftPane(LeftPaneType.LIST_CHECKS);
        });

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

        TextFlow banner = new TextFlow()
        {
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

        checksLabel = new CheckSummaryLabel(v.getManager(), () -> FXUtility.mouse(this).showLeftPane(LeftPaneType.LIST_CHECKS));
        BorderPane statusBar = GUI.borderLeftRight(checksLabel, null, "status-bar");
        statusBar.visibleProperty().bind(checksLabel.hasChecksProperty());
        statusBar.managedProperty().bind(statusBar.visibleProperty());

        root = new BorderPane(stackPane, menuBar, null, statusBar, null);
        Scene scene = new Scene(root);
        scene.getStylesheets().addAll(FXUtility.getSceneStylesheets("mainview.css"));
        stage.setScene(scene);
        stage.setWidth(1000);
        stage.setHeight(800);
        FXUtility.setIcon(stage);

        if (src != null)
        {
            @NonNull Pair<File, String> srcFinal = src;
            Workers.onWorkerThread("Load", Priority.LOAD_FROM_DISK, () -> FXUtility.alertOnError_(TranslationUtility.getString("error.loading", srcFinal.getFirst().getName()), err -> {
                Platform.runLater(() -> updateBanner(v, banner, false));
                return TranslationUtility.getString("error.loading.file", srcFinal.getFirst().getAbsolutePath(), err);
            }, () -> {
                v.getManager().setBanAllR(!View.checkHashMatch(srcFinal.getFirst(), Hashing.sha256().hashString(srcFinal.getSecond(), StandardCharsets.UTF_8)));
                v.getManager().loadAll(srcFinal.getSecond(), v::loadColumnWidths);
                v.getManager().setBanAllR(false);
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
    }
    
    private static enum LeftPaneType
    { NONE, LIST_CHECKS }

    private void showLeftPane(LeftPaneType leftPaneType)
    {
        if (leftPaneType == curLeftPaneType)
            return;
        curLeftPaneType = leftPaneType;
        
        if (root.getLeft() instanceof LeftPane)
            ((LeftPane)root.getLeft()).cleanup();
        switch (leftPaneType)
        {
            case NONE:
                root.setLeft(null);
                viewLeftToggleGroup.selectToggle(viewLeftNone);
                break;
            case LIST_CHECKS:
                root.setLeft(new ChecksLeftPane(checksLabel));
                viewLeftToggleGroup.selectToggle(viewLeftChecks);
                break;
        }
    }

    private MainWindowActions getActions()
    {
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

    @OnThread(Tag.FXPlatform)
    public static Map<View, Stage> _test_getViews()
    {
        return views;
    }
    
    @OnThread(Tag.FXPlatform)
    private class LeftPane extends StackPane
    {
        private SmallDeleteButton closeButton = new SmallDeleteButton();

        public LeftPane()
        {
            StackPane.setAlignment(closeButton, Pos.TOP_RIGHT);
            StackPane.setMargin(closeButton, new Insets(8, 8, 0, 0));
            closeButton.setOnAction(() -> showLeftPane(LeftPaneType.NONE));
        }
        
        protected void setContent(@UnknownInitialization(LeftPane.class) LeftPane this, Node content)
        {
            getChildren().setAll(content, closeButton);
        }

        protected void cleanup()
        {
        }
    }

    @OnThread(Tag.FXPlatform)
    private final class ChecksLeftPane extends LeftPane implements ChecksStateListener
    {
        private final CheckSummaryLabel checkSummaryLabel;
        private final VBox checkList;

        public ChecksLeftPane(CheckSummaryLabel checkSummaryLabel)
        {
            this.checkSummaryLabel = checkSummaryLabel;
            setMinWidth(150);
            setMaxWidth(250);
            checkList = GUI.vbox("checks-list");
            checkSummaryLabel.addListener(this);

            updateGUI();

            setContent(new ScrollPaneFill(checkList) {
                @Override
                public void requestFocus()
                {
                    // Don't allow focusing
                }
            });
        }

        @Override
        public void checksChanged()
        {
            updateGUI();
        }

        private void updateGUI()
        {
            Workers.onWorkerThread("Updating checks pane", Priority.FETCH, () -> {
                ImmutableList<Check> failingChecks = checkSummaryLabel.getFailingChecks();
                ImmutableList<Check> passingChecks = checkSummaryLabel.getPassingChecks();
                
                FXUtility.runFX(() -> {

                    checkList.getChildren().clear();
                    Label top = new Label("Checks");
                    top.getStyleClass().add("checks-top-header");
                    checkList.getChildren().add(top);


                    if (!failingChecks.isEmpty())
                    {
                        checkList.getChildren().add(makeHeader("Failing"));
                        for (Check check : failingChecks)
                        {
                            checkList.getChildren().add(makeItem(check, false));
                        }
                    }

                    if (!passingChecks.isEmpty())
                    {
                        checkList.getChildren().add(makeHeader("Succeeding"));
                        for (Check check : passingChecks)
                        {
                            checkList.getChildren().add(makeItem(check, true));
                        }
                    }
                });
            });
        }

        @Override
        protected void cleanup()
        {
            checkSummaryLabel.removeListener(this);
        }

        private Label makeItem(Check check, boolean passing)
        {
            Label label = new Label(check.getId().getRaw());
            FXUtility.setPseudoclass(label, "failing", !passing);
            label.setOnMouseClicked(e -> {
                TableDisplayBase display = check.getDisplay();
                if (display instanceof HeadedDisplay)
                    v.getGrid().findAndSelect(Either.right(new EntireTableSelection((HeadedDisplay)display, display.getMostRecentPosition().columnIndex)));
            });
            VBox.setMargin(label, new Insets(0, 0, 0, 20));
            label.getStyleClass().add("checks-list-entry");
            return label;
        }

        private Label makeHeader(String header)
        {
            Label label = new Label(header);
            label.getStyleClass().add("checks-list-header");
            return label;
        }
    }
}
