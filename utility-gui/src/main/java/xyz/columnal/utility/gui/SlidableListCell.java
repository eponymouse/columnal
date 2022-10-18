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

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.control.ListCell;
import javafx.util.Duration;
import org.checkerframework.checker.nullness.qual.Nullable;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.function.fx.FXPlatformRunnable;

import java.util.List;

/**
 * Allows you to slide a list cell's content left or right during an animation.
 *
 * Note: only works with a graphic, not with text (just use a Label, if you want text).
 */
public class SlidableListCell<T> extends ListCell<T>
{
    @OnThread(Tag.FX)
    protected final DoubleProperty xPosition = new SimpleDoubleProperty(0);

    public SlidableListCell()
    {
        textProperty().addListener(c -> { throw new UnsupportedOperationException("SlidableListCell only supports graphic, not text.");});
        xPosition.addListener(c -> requestLayout());
    }

    @OnThread(Tag.FXPlatform)
    public static void animateOutToRight(List<? extends SlidableListCell<?>> cells, @Nullable FXPlatformRunnable after)
    {
        for (SlidableListCell<?> cell : cells)
        {
            cell.xPosition.setValue(0);
        }

        Timeline t = new Timeline(new KeyFrame(Duration.millis(200),
            cells.stream().map(c -> new KeyValue(c.xPosition, c.getWidth())).toArray(KeyValue[]::new)));

        if (after != null)
        {
            FXPlatformRunnable afterFinal = after;
            t.setOnFinished(e -> afterFinal.run());
        }
        t.play();
    }

    @OnThread(Tag.FXPlatform)
    public static void animateInFromLeft(List<? extends SlidableListCell<?>> cells, @Nullable FXPlatformRunnable after)
    {
        for (SlidableListCell<?> cell : cells)
        {
            cell.xPosition.setValue(-cell.getWidth());
        }

        Timeline t = new Timeline(new KeyFrame(Duration.millis(200),
            cells.stream().map(c -> new KeyValue(c.xPosition, 0)).toArray(KeyValue[]::new)));

        if (after != null)
        {
            FXPlatformRunnable afterFinal = after;
            t.setOnFinished(e -> afterFinal.run());
        }
        t.play();
    }

    @Override
    @OnThread(Tag.FX)
    protected void updateItem(@Nullable T item, boolean empty)
    {
        super.updateItem(item, empty);
        xPosition.setValue(0);
    }

    @Override
    @OnThread(Tag.FX)
    protected void layoutChildren()
    {
        final double x = snappedLeftInset();
        final double y = snappedTopInset();
        final double w = snapSizeX(getWidth()) - x - snappedRightInset();
        final double h = snapSizeY(getHeight()) - y - snappedBottomInset();
        getGraphic().resizeRelocate(x + xPosition.get(), y, w - Math.max(0, xPosition.get()), h);
        //super.layoutChildren();
    }


}
