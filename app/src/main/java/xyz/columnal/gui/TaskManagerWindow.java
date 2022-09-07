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

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Modality;
import javafx.util.Duration;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Workers;
import xyz.columnal.utility.Workers.WorkInfo;
import xyz.columnal.utility.gui.FXUtility;
import xyz.columnal.utility.gui.GUI;

@OnThread(Tag.FXPlatform)
public class TaskManagerWindow extends Dialog<Void>
{
    private static @MonotonicNonNull TaskManagerWindow SINGLETON;
    private final ObservableList<Workers.WorkInfo> taskListMirror = FXCollections.observableArrayList();

    private TaskManagerWindow()
    {
        initModality(Modality.NONE);
        getDialogPane().getButtonTypes().setAll(ButtonType.OK);
        TableView<WorkInfo> tableView = new TableView<>(taskListMirror);
        getDialogPane().setContent(tableView);
        tableView.getColumns().add(GUI.tableColumn("Task Name", t -> t.taskName));
        tableView.getColumns().add(GUI.tableColumn("Priority", p -> p.priority));
        refreshTaskList();
        Timeline t = new Timeline(new KeyFrame(Duration.seconds(3), e -> refreshTaskList()));
        t.setCycleCount(Animation.INDEFINITE);
        setOnShown(e -> t.playFromStart());
        setOnHidden(e -> t.stop());
    }

    @RequiresNonNull("taskListMirror")
    private void refreshTaskList(@UnknownInitialization(Object.class) TaskManagerWindow this)
    {
        taskListMirror.setAll(Workers.getTaskList());
    }

    public static TaskManagerWindow getInstance()
    {
        if (SINGLETON == null)
            SINGLETON = new TaskManagerWindow();
        return SINGLETON;
    }
}
