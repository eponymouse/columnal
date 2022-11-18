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
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import org.apache.commons.lang3.SystemUtils;
import org.testjavafx.FxRobotInterface;
import test.gui.TFXUtil;
import test.gui.trait.FocusOwnerTrait;
import threadchecker.OnThread;
import threadchecker.Tag;

import static org.junit.Assert.assertTrue;

public interface TextFieldTrait extends FxRobotInterface, FocusOwnerTrait
{
    @OnThread(Tag.Any)
    public default TextInputControl selectAllCurrentTextField()
    {
        Node focusOwner = getFocusOwner();
        if (!(focusOwner instanceof TextInputControl))
            throw new RuntimeException("Focus owner is " + (focusOwner == null ? "null" : focusOwner.getClass().toString()) 
                + "\nTarget window is " + TFXUtil.fx(() -> focusedWindows())
                //+ "\nOut of " + TFXUtil.fx(() -> Streams.stream(Window.impl_getWindows()).map(w -> w.toString() + ":" + w.isFocused()).collect(Collectors.joining("/")))
            );
        TextInputControl textField = (TextInputControl) focusOwner;

        assertTrue(TFXUtil.fx(textField::isEditable));
        assertTrue(!TFXUtil.fx(textField::isDisabled));
        assertTrue(TFXUtil.fx(textField::isFocused));
        
        // Some sort of bug on OS X prevents Cmd-A working in TestFX:
        if (SystemUtils.IS_OS_MAC_OSX)
            TFXUtil.fx_(() -> textField.selectAll());
        else
            push(KeyCode.CONTROL, KeyCode.A);
        return textField;
    }
}
