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

package test.gui.trait;

import javafx.scene.Node;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.testjavafx.FxRobotInterface;
import test.gui.TFXUtil;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Utility;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertTrue;

public interface FocusOwnerTrait extends FxRobotInterface
{
    @OnThread(Tag.Any)
    default @Nullable Node getFocusOwner()
    {
        Window curWindowFinal = TFXUtil.fx(() -> getRealFocusedWindow());
        return TFXUtil.fx(() -> curWindowFinal.getScene().getFocusOwner());
    }

    @OnThread(Tag.Any)
    default Window fxGetRealFocusedWindow()
    {
        return TFXUtil.fx(this::getRealFocusedWindow);
    }
    

    // Ignores PopupWindow
    @OnThread(Tag.FXPlatform)
    default Window getRealFocusedWindow()
    {
        
        return focusedWindows().stream().filter(w -> w.getScene() != null && w.getScene().getFocusOwner() != null).findFirst().orElse(null);
        /*
        // The only children of Window are PopupWindow, Stage and EmbeddedWindow.
        // We are not interested in popup or embedded so we may as well
        // filter down to Stage:
        List<Stage> curWindow = new ArrayList<>(
            Utility.filterClass(
                listWindows().stream().filter(Window::isFocused),
                Stage.class)
            .collect(Collectors.toList()));
        if (curWindow.isEmpty())
            throw new RuntimeException("No focused window?!  Options were: " + Utility.listToString(listWindows()));
        // It seems that (only in Monocle?) multiple windows can claim to
        // have focus when a main window shows sub-dialogs, so we have to manually
        // try to work out the real focused window:
        if (curWindow.size() > 1)
        {
            // Remove any windows claiming to be focused which have a child
            // window that is focused:
            TFXUtil.fx_(() -> {
                curWindow.removeIf(w -> curWindow.stream().anyMatch(parent -> parent instanceof Stage && ((Stage) parent).getOwner() == w));
            });
        }
        // Fall back to targetWindow if we still haven't narrowed it down:
        return curWindow.size() == 1 ? curWindow.get(0) : focusedWindow();
         */
    }

    @OnThread(Tag.Any)
    default <C extends Node> C getFocusOwner(Class<C> expectedClass)
    {
        Node node = getFocusOwner();
        if (!expectedClass.isInstance(node))
            throw new RuntimeException("Focus owner is " + (node == null ? "null" : node.getClass().toString()) + " but expected " + expectedClass + " Target window: " + TFXUtil.fx(() -> focusedWindows()) + " Real focused window: " + TFXUtil.fx(() -> getRealFocusedWindow()));
        return expectedClass.cast(node);
    }

    @OnThread(Tag.Any) 
    default FxRobotInterface correctTargetWindow()
    {
        return this; // TFXUtil.fx(() -> targetWindow(getRealFocusedWindow()));
    }
    
    default public void checkDialogFocused(String msg)
    {
        Window window = fxGetRealFocusedWindow();
        assertTrue(msg + " " + window + " " + window.getClass(), window.getClass().toString().contains("Dialog"));
    }
    
}
