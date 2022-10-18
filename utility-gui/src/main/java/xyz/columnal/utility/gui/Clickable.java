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

import javafx.geometry.Point2D;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.text.Text;
import javafx.util.Duration;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.styled.StyledString.Style;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.function.fx.FXPlatformRunnable;
import xyz.columnal.utility.TranslationUtility;

@OnThread(Tag.FXPlatform)
public abstract class Clickable extends Style<Clickable>
{
    private final @Nullable @LocalizableKey String tooltipKey;
    private final String[] extraStyleClasses;

    public Clickable()
    {
        this("click.to.change");
    }
    
    public Clickable(@Nullable @LocalizableKey String tooltipKey, String... styleClasses)
    {
        super(Clickable.class);
        this.tooltipKey = tooltipKey;
        this.extraStyleClasses = styleClasses;
    }

    @OnThread(Tag.FXPlatform)
    protected abstract void onClick(MouseButton mouseButton, Point2D screenPoint);
    
    public final void _test_onClick(MouseButton mouseButton, Point2D screenPoint)
    {
        onClick(mouseButton, screenPoint);
    }
    
    protected void setHovering(boolean hovering, Point2D screenPos)
    {
    }
    
    private class HoverTracker
    {
        private boolean hovering = false;
        private @Nullable FXPlatformRunnable cancelHover = null;
        
        public void start(MouseEvent e)
        {
            stop(e);
            cancelHover = FXUtility.runAfterDelay(Duration.millis(300), () -> {
                if (!hovering)
                {
                    hovering = true;
                    cancelHover = null;
                    setHovering(true, new Point2D(e.getScreenX(), e.getScreenY()));
                }
            });
        }
        
        public void stop(MouseEvent e)
        {
            hovering = false;
            if (cancelHover != null)
            {
                cancelHover.run();
                cancelHover = null;
            }
            setHovering(false, new Point2D(e.getScreenX(), e.getScreenY()));
        }
    }

    @Override
    @OnThread(Tag.FXPlatform)
    protected void style(Text t)
    {
        t.getStyleClass().add("styled-text-clickable");
        t.getStyleClass().addAll(extraStyleClasses);
        HoverTracker hoverTracker = new HoverTracker();
        t.setOnMouseClicked(e -> {
            hoverTracker.stop(e);
            setHovering(false, new Point2D(e.getScreenX(), e.getScreenY()));
            onClick(e.getButton(), new Point2D(e.getScreenX(), e.getScreenY()));
        });
        // For testing:
        t.setUserData(this);
        
        t.setOnMouseEntered(e -> hoverTracker.start(e));
        t.setOnMouseMoved(e -> hoverTracker.start(e));
        t.setOnMouseExited(e -> hoverTracker.stop(e));
        if (tooltipKey != null)
        {
            Tooltip tooltip = new Tooltip(TranslationUtility.getString(tooltipKey));
            Tooltip.install(t, tooltip);
        }
    }

    @Override
    @OnThread(Tag.Any)
    protected Clickable combine(Clickable with)
    {
        // Cannot combine, so make arbitrary choice:
        return this;
    }
    
    @Override
    @OnThread(Tag.Any)
    protected boolean equalsStyle(Clickable item)
    {
        return false;
    }
}
