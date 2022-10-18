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
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.commons.io.FileUtils;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.gui.Main.UpgradeInfo;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.gui.dialog.AboutDialog;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.TranslationUtility;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.gui.DimmableParent.Undimmed;
import xyz.columnal.utility.gui.FXUtility;
import xyz.columnal.utility.gui.GUI;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/**
 * Created by neil on 17/04/2017.
 */
@OnThread(Tag.FXPlatform)
public class InitialWindow
{
    public static void show(Stage stage, @Nullable CompletionStage<Optional<UpgradeInfo>> upgradeInfo)
    {
        MenuBar menuBar = new MenuBar(
            GUI.menu("menu.project",
                GUI.menuItem("menu.project.new", () -> {
                    newProject(stage, upgradeInfo);
                    stage.hide();
                }),
                GUI.menuItem("menu.project.open", () -> {
                    if (chooseAndOpenProject(stage, upgradeInfo))
                        stage.hide();
                }),
                GUI.menu("menu.project.open.recent", InitialWindow.makeRecentProjectMenu(stage, upgradeInfo).toArray(new MenuItem[0])),
                GUI.menuItem("menu.exit", () -> {
                    MainWindow.closeAll();
                    stage.hide();
                })
            ),
            GUI.menu("menu.help",
                GUI.menuItem("menu.help.about", () -> new AboutDialog(new Undimmed(stage)).showAndWait())
            )
        );
        menuBar.setUseSystemMenuBar(true);
        Button newButton = GUI.button("initial.new", () -> {
            newProject(stage, upgradeInfo);
            stage.hide();
        });
        Button openButton = GUI.button("initial.open", () -> {
            if (chooseAndOpenProject(stage, upgradeInfo))
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
                    openProject(stage, selected, upgradeInfo);
                }
            }
        });
        mruListView.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.SPACE)
            {
                @Nullable File selected = mruListView.getSelectionModel().getSelectedItem();
                if (selected != null)
                {
                    openProject(stage, selected, upgradeInfo);
                }
            }
        });
        
        mruListView.getItems().setAll(Utility.readRecentFilesList());
        ImageView title = FXUtility.makeImageView("columnal.png", null, 90);
        ImageView logo = FXUtility.makeImageView("logo.png", null, 100);
        HBox hBox = new HBox(Utility.streamNullable(title, logo).toArray(Node[]::new));
        hBox.getStyleClass().add("logo-container");
        hBox.setAlignment(Pos.BOTTOM_CENTER);
        
        VBox content = GUI.vbox("initial-content",
                hBox,
                GUI.vbox("body-container",
                    headed("initial-section-new", GUI.label("initial.new.title", "initial-heading"), newButton, GUI.labelWrap("initial.new.detail")),
                    headed("initial-section-open", GUI.label("initial.open.title", "initial-heading"), openButton, GUI.vbox("initial-recent", GUI.label("initial.open.recent", "initial-subheading"), mruListView))
                )
        );
        if (upgradeInfo != null)
        {
            upgradeInfo.thenAccept(new Consumer<Optional<UpgradeInfo>>()
            {
                @Override
                @OnThread(value = Tag.Worker, ignoreParent = true)
                public void accept(Optional<UpgradeInfo> opt)
                {
                    opt.ifPresent(u -> Platform.runLater(() -> u.showIn(content, 2)));
                }
            });
        }
        Scene scene = new Scene(new BorderPane(content, menuBar, null, null, null));
        scene.getStylesheets().addAll(FXUtility.getSceneStylesheets("initial"));
        stage.setScene(scene);
        stage.setTitle(TranslationUtility.getString("us"));
        FXUtility.setIcon(stage);
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
    public static boolean chooseAndOpenProject(Stage parent, @Nullable CompletionStage<Optional<UpgradeInfo>> upgradeInfo)
    {
        File src = FXUtility.chooseFileOpen("project.open.dialogTitle", "projectOpen", parent, FXUtility.getProjectExtensionFilter(Main.EXTENSION_INCL_DOT));
        if (src != null)
        {
            return openProject(parent, src, upgradeInfo);
        }
        return false;
    }

    // Returns true if successfully opened a project
    private static boolean openProject(Stage parent, File src, @Nullable CompletionStage<Optional<UpgradeInfo>> upgradeInfo)
    {
        try
        {
            MainWindow.show(new Stage(), src, new Pair<>(src, FileUtils.readFileToString(src, "UTF-8")), upgradeInfo);
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
    public static MainWindow.@Nullable MainWindowActions newProject(@Nullable Stage parent, @Nullable CompletionStage<Optional<UpgradeInfo>> upgradeInfo)
    {
        return FXUtility.<MainWindow.@Nullable MainWindowActions>alertOnErrorFX(TranslationUtility.getString("error.creating.new.file"), () ->
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
                return MainWindow.show(new Stage(), dest, null, upgradeInfo);
            else
                return null;
        });
    }
    
    static ImmutableList<MenuItem> makeRecentProjectMenu(Stage window, @Nullable CompletionStage<Optional<UpgradeInfo>> upgradeInfo)
    {
        return Utility.mapListI(Utility.readRecentFilesList(), recent -> {
            MenuItem menuItem = new MenuItem(recent.getAbsolutePath());
            menuItem.setOnAction(e -> {
                openProject(window, recent, upgradeInfo);
            });
            return menuItem;
        });
    }
}
