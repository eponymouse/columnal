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
import javafx.scene.Node;
import org.checkerframework.checker.nullness.qual.Nullable;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Utility;

import java.util.ArrayList;
import java.util.Iterator;

// Allows tracking of focus
public class FocusTracker
{
    private final ArrayList<TimedFocusable> items = new ArrayList<>();
    
    public void addNode(Node... nodes)
    {
        for (Node node : nodes)
        {
            if (node instanceof TimedFocusable)
                items.add((TimedFocusable) node);
            else
                items.add(new Wrapper(node));
        }
    }
    
    public void removeNode(Node... nodes)
    {
        for (Node node : nodes)
        {
            for (Iterator<TimedFocusable> it = items.iterator(); it.hasNext();)
            {
                TimedFocusable t = it.next();
                if (t == node)
                    it.remove();
                else if (t instanceof Wrapper && ((Wrapper)t).wrapped == node)
                {
                    node.focusedProperty().removeListener((Wrapper)t);
                    it.remove();
                }
            }
        }
    }

    public @Nullable Node getRecentlyFocused()
    {
        TimedFocusable timedFocusable = TimedFocusable.getRecentlyFocused(items.toArray(new TimedFocusable[0]));
        if (timedFocusable instanceof Node)
            return (Node) timedFocusable;
        else if (timedFocusable instanceof Wrapper)
            return ((Wrapper) timedFocusable).wrapped;
        else
            return null;
    }

    private static class Wrapper implements TimedFocusable, ChangeListener<Boolean>
    {
        private final Node wrapped;
        private long lastFocusTime;

        public Wrapper(Node wrapped)
        {
            this.wrapped = wrapped;
            if (this.wrapped.isFocused())
                lastFocusTime = System.currentTimeMillis();
            else
                lastFocusTime = 0L;
            wrapped.focusedProperty().addListener(Utility.later(this));
        }

        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        public void changed(ObservableValue<? extends Boolean> observable, Boolean wasFocused, Boolean newValue)
        {
            if (wasFocused)
                lastFocusTime = System.currentTimeMillis(); 
        }

        @Override
        @OnThread(Tag.FXPlatform)
        public long lastFocusedTime()
        {
            return wrapped.isFocused() ? System.currentTimeMillis() : lastFocusTime;
        }
    }
}
