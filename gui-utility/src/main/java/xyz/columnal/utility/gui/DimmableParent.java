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

import javafx.scene.control.Dialog;
import javafx.stage.Window;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.function.fx.FXPlatformFunction;

public interface DimmableParent
{
    @OnThread(Tag.FXPlatform)
    public Window dimWhileShowing(@UnknownInitialization(Dialog.class) Dialog<?> dialog);

    // Only use for things like file choosers where you can't get a reference to the dialog.
    @OnThread(Tag.FXPlatform)
    public <T> T dimAndWait(FXPlatformFunction<Window, T> showAndWait);
    
    public static class Undimmed implements DimmableParent
    {
        private final Window window;

        public Undimmed(Window w)
        {
            this.window = w;
        }

        @Override
        public @OnThread(Tag.FXPlatform) Window dimWhileShowing(@UnknownInitialization(Dialog.class) Dialog<?> dialog)
        {
            return window;
        }

        @Override
        public <T> @OnThread(Tag.FXPlatform) T dimAndWait(FXPlatformFunction<Window, T> showAndWait)
        {
            return showAndWait.apply(window);
        }
    }
}
