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
import javafx.stage.Window;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.testjavafx.FxRobotInterface;
import test.gui.TFXUtil;
import threadchecker.OnThread;
import threadchecker.Tag;

import static org.junit.Assert.assertTrue;

public interface FocusOwnerTrait extends FxRobotInterface
{
    @OnThread(Tag.Any)
    default @Nullable Node getFocusOwner()
    {
        Window curWindowFinal = targetWindow();
        return TFXUtil.fx(() -> curWindowFinal.getScene().getFocusOwner());
    }

    @OnThread(Tag.Any)
    default <C extends Node> C getFocusOwner(Class<C> expectedClass)
    {
        Node node = getFocusOwner();
        if (!expectedClass.isInstance(node))
            throw new RuntimeException("Focus owner is " + (node == null ? "null" : node.getClass().toString()) + " but expected " + expectedClass + " Target window: " + TFXUtil.fx(() -> focusedWindows()) + " Real focused window: " + targetWindow());
        return expectedClass.cast(node);
    }
    
    default public void checkDialogFocused(String msg)
    {
        Window window = targetWindow();
        assertTrue(msg + " " + window + " " + window.getClass(), window.getClass().toString().contains("Dialog"));
    }
    
}
