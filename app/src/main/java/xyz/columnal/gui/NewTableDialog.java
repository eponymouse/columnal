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

import javafx.geometry.Point2D;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import xyz.columnal.gui.NewTableDialog.DataOrTransform;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.gui.DimmableParent;
import xyz.columnal.utility.gui.FXUtility;
import xyz.columnal.utility.gui.GUI;
import xyz.columnal.utility.gui.LightDialog;

import java.util.Optional;

/**
 * The dialog that allows picking between making new data source
 * and making new transform.
 * 
 * Returns a pair of mouse screen position, and choice
 */
@OnThread(Tag.FXPlatform)
public class NewTableDialog extends LightDialog<Pair<Point2D, DataOrTransform>>
{
    private static final double WIDTH = 380;
    private static final double HEIGHT = 290;

    public NewTableDialog(DimmableParent parent, boolean transformIsValid)
    {
        super(parent);
        
        @UnknownInitialization(Dialog.class) NewTableDialog us = NewTableDialog.this;
        Button transformButton = new ExplainedButton("new.transform", "new.transform.explanation", NewTableDialog.WIDTH * 0.45, p -> {
            us.setResult(new Pair<>(p, DataOrTransform.TRANSFORM));
            close();
        });
        transformButton.setDisable(!transformIsValid);
        VBox.setVgrow(transformButton, Priority.ALWAYS);
        Button checkButton = new ExplainedButton("new.check", "new.check.explanation", NewTableDialog.WIDTH * 0.45, p -> {
            us.setResult(new Pair<>(p, DataOrTransform.CHECK));
            close();
        });
        checkButton.setDisable(!transformIsValid);

        Button immediateDataButton = new ExplainedButton("new.data", "new.data.explanation", NewTableDialog.WIDTH * 0.45, p -> {
            us.setResult(new Pair<>(p, DataOrTransform.DATA));
            close();
        });
        setOnShown(e -> immediateDataButton.requestFocus());
        VBox.setVgrow(immediateDataButton, Priority.ALWAYS);
        Button importFromFile = new ExplainedButton("import.file", "import.file.explanation", NewTableDialog.WIDTH * 0.45, p -> {
            us.setResult(new Pair<>(p, DataOrTransform.IMPORT_FILE));
            close();
        });
        Button importFromLink = new ExplainedButton("import.url", "import.url.explanation", NewTableDialog.WIDTH * 0.45, p -> {
            us.setResult(new Pair<>(p, DataOrTransform.IMPORT_URL));
            close();
        });
        
        Button newComment = new ExplainedButton("new.comment", "new.comment.explanation", NewTableDialog.WIDTH * 0.45, p -> {
            us.setResult(new Pair<>(p, DataOrTransform.COMMENT));
            close();
        });
        
        Label explanation = new Label("Tables are either plain data (left) or transformations of other tables (right)");
        explanation.getStyleClass().add("new-explanation");
        explanation.setWrapText(true);
        
        BorderPane content = new BorderPane(null, explanation, GUI.vbox("new-button-list", transformButton, checkButton), null, GUI.vbox("new-button-list", immediateDataButton, importFromFile, importFromLink, newComment));
        FXUtility.forcePrefSize(content);
        content.setPrefWidth(WIDTH);
        content.setPrefHeight(HEIGHT);
        getDialogPane().setContent(content);
        getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL);
        setResultConverter(bt -> null);
        centreDialogButtons();
        //org.scenicview.ScenicView.show(getDialogPane().getScene());
    }

    public Optional<Pair<Point2D, DataOrTransform>> showAndWaitCentredOn(Point2D mouseScreenPos)
    {
        return super.showAndWaitCentredOn(mouseScreenPos, WIDTH, HEIGHT);
    }

    public static enum DataOrTransform {DATA, IMPORT_FILE, IMPORT_URL, COMMENT, TRANSFORM, CHECK };

}
