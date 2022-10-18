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
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import xyz.columnal.transformations.TransformationInfo;
import xyz.columnal.transformations.TransformationManager;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.gui.DimmableParent;
import xyz.columnal.utility.gui.FXUtility;
import xyz.columnal.utility.gui.LightDialog;

import java.util.Optional;

@OnThread(Tag.FXPlatform)
public class PickTransformationDialog extends LightDialog<Pair<Point2D, TransformationInfo>>
{
    private static final int BUTTON_WIDTH = 150;
    private static final int BUTTON_HEIGHT = 165;
    private static final double WIDTH = BUTTON_WIDTH * 4 + 3 * 10;
    private static final double HEIGHT = BUTTON_HEIGHT * 2 + 3 * 10;

    public PickTransformationDialog(DimmableParent parent)
    {
        super(parent);
        setResizable(true);
        
        GridPane gridPane = new GridPane();
        gridPane.getStyleClass().add("pick-transformation-tile-pane");

        makeTransformationButtons(gridPane);
        
        FXUtility.forcePrefSize(gridPane);
        ScrollPane scrollPane = new ScrollPane(gridPane) {
            @Override
            public void requestFocus()
            {
            }
        };
        scrollPane.getStyleClass().add("pick-transformation-scroll-pane");
        scrollPane.setHbarPolicy(ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollBarPolicy.ALWAYS);
        BorderPane borderPane = new BorderPane(scrollPane);
        borderPane.getStyleClass().add("pick-transformation-scroll-pane-wrapper");
        borderPane.setPrefHeight(HEIGHT + 50);
        getDialogPane().setContent(borderPane);
        getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL);
        setResultConverter(bt -> null);
        centreDialogButtons();
        //org.scenicview.ScenicView.show(getDialogPane().getScene());
    }

    private void makeTransformationButtons(@UnknownInitialization(LightDialog.class) PickTransformationDialog this, GridPane gridPane)
    {
        int column = 0;
        int row = 0;
        for (TransformationInfo transformationInfo : TransformationManager.getInstance().getTransformations())
        {
            // Check is shown in the first dialog:
            if (transformationInfo.getCanonicalName().equals("check"))
                continue;
            Button button = new ExplainedButton(transformationInfo.getDisplayNameKey(), transformationInfo.getExplanationKey(), transformationInfo.getImageFileName(), BUTTON_WIDTH, p -> {
                FXUtility.mouse(this).setResult(new Pair<>(p, transformationInfo));
                close();
            });
            gridPane.add(button, column, row);
            column = (column + 1) % 3;
            if (column == 0)
                row += 1;
        }
    }

    public Optional<Pair<Point2D, TransformationInfo>> showAndWaitCentredOn(Point2D mouseScreenPos)
    {
        return super.showAndWaitCentredOn(mouseScreenPos, WIDTH, HEIGHT);
    }
}
