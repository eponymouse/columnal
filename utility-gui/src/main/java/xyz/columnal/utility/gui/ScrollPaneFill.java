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

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ScrollPane;
import xyz.columnal.log.Log;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * A ScrollPane which expands its content to fill the width and height of the scroll pane,
 * if the scroll pane is larger than the contained content.
 *
 * Note that setting fitWidthProperty() alone does not do this: that property constrains the
 * width of the item to always fit the width.  This class allows a horizontal scroll
 * if the content is too big, but expands when the content is too small.  (Same for fitHeightProperty())
 *
 * See https://reportmill.wordpress.com/2014/06/03/make-scrollpane-content-fill-viewport-bounds/
 * for origin of trick.
 */
public class ScrollPaneFill extends ScrollPane
{
    private boolean alwaysFitToWidth = false;

    public ScrollPaneFill()
    {
        getStyleClass().add("scroll-pane-fill");
        FXUtility.addChangeListenerPlatform(viewportBoundsProperty(), b -> FXUtility.runAfter(() -> fillViewport(b)));
        final ChangeListener<Bounds> boundListener = new ViewportFillListener();
        contentProperty().addListener(new ChangeListener<Node>()
        {
            @Override
            @OnThread(value = Tag.FXPlatform, ignoreParent = true)
            public void changed(ObservableValue<? extends Node> prop, Node prev, Node now)
            {
                fillViewport();
                if (prev != null)
                    prev.boundsInLocalProperty().removeListener(boundListener);
                if (now != null)
                    now.boundsInLocalProperty().addListener(boundListener);
                if (now != null && !now.isResizable())
                    Log.logStackTrace("Unresizable content in ScrollPaneFill: " + now.getClass());
            }
        });
    }

    public ScrollPaneFill(Node content)
    {
        this();
        setContent(content);
    }
    
    @OnThread(Tag.FXPlatform)
    public void setAlwaysFitToWidth(boolean alwaysFitToWidth)
    {
        this.alwaysFitToWidth = alwaysFitToWidth;
    }

    @OnThread(Tag.FXPlatform)
    private void fillViewport(@UnknownInitialization(ScrollPane.class) ScrollPaneFill this, @Nullable Bounds viewportBounds)
    {
        if (viewportBounds != null)
        {
            Node content = getContent();
            setFitToWidth(alwaysFitToWidth || content.prefWidth(-1) < viewportBounds.getWidth());
            setFitToHeight(content.prefHeight(viewportBounds.getWidth()) < viewportBounds.getHeight());
            requestLayout();
            if (content instanceof Parent)
                ((Parent)content).requestLayout();
        }
    }

    // Call this direct if you have altered what is inside the content in the viewport:
    @OnThread(Tag.FXPlatform)
    public void fillViewport(@UnknownInitialization(ScrollPane.class) ScrollPaneFill this)
    {
        fillViewport(getViewportBounds());
    }

    private class ViewportFillListener implements ChangeListener<Bounds>
    {
        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        public void changed(ObservableValue<? extends Bounds> a, Bounds b, Bounds c)
        {
            fillViewport();
        }
    }
}
