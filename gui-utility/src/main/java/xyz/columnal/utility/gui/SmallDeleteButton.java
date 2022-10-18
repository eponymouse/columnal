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

package xyz.columnal.utility.gui;

import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.Shape;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.function.fx.FXPlatformConsumer;
import xyz.columnal.utility.function.fx.FXPlatformRunnable;

/**
 * A cross shape, like the close on a web browser tab header
 */
@OnThread(Tag.FX)
public class SmallDeleteButton extends StackPane
{
    // Composed of two shapes: a cross, with a circle underneath
    private final Shape circle;
    private final Shape cross;

    public SmallDeleteButton()
    {
        circle = new Circle(8);
        cross = new Path(
            new MoveTo(0, 0),
            new LineTo(8, 8),
            new MoveTo(8, 0),
            new LineTo(0, 8));
        getStyleClass().add("small-delete");
        circle.getStyleClass().add("small-delete-circle");
        cross.getStyleClass().add("small-delete-cross");
        circle.setMouseTransparent(true);
        cross.setMouseTransparent(true);
        getChildren().addAll(circle, cross);
        setOnMousePressed(MouseEvent::consume);
        setOnMouseReleased(MouseEvent::consume);
        setOnMouseClicked(MouseEvent::consume);
    }

    public void setOnAction(FXPlatformRunnable onAction)
    {
        setOnMouseClicked(e -> onAction.run());
    }

    /**
     * Given action will be called with true on hover, and false on exit
     * @param onHover
     */
    public void setOnHover(FXPlatformConsumer<Boolean> onHover)
    {
        setOnMouseEntered(e -> onHover.consume(true));
        setOnMouseExited(e -> onHover.consume(false));
    }

    @Override
    protected double computeMinWidth(double height)
    {
        return 8;
    }

    @Override
    protected double computeMinHeight(double width)
    {
        return 8;
    }

    @Override
    protected double computePrefWidth(double height)
    {
        return 8;
    }

    @Override
    protected double computePrefHeight(double width)
    {
        return 8;
    }

    @Override
    protected double computeMaxWidth(double height)
    {
        return 8;
    }

    @Override
    protected double computeMaxHeight(double width)
    {
        return 8;
    }
}
