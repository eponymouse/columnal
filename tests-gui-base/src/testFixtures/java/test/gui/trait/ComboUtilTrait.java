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

import javafx.collections.ObservableList;
import javafx.scene.control.ComboBox;
import javafx.scene.input.KeyCode;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.testjavafx.FxRobotInterface;
import test.gui.TFXUtil;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Utility;

import static org.junit.Assert.fail;

/**
 * Created by neil on 11/06/2017.
 */
public interface ComboUtilTrait extends FxRobotInterface
{
    @OnThread(Tag.Any)
    default <T> void selectNextComboBoxItem(final ComboBox<T> combo) {
        clickOn(combo).push(KeyCode.DOWN).push(KeyCode.ENTER);
    }

    @OnThread(Tag.Any)
    default <@NonNull T> void selectGivenComboBoxItem(final ComboBox<@NonNull T> combo, final @NonNull T item) {
        ObservableList<@NonNull T> comboItems = TFXUtil.<ObservableList<@NonNull T>>fx(() -> combo.getItems());
        final int index = comboItems.indexOf(item);
        final int indexSel = TFXUtil.fx(() -> combo.getSelectionModel().getSelectedIndex());

        if(index == -1)
            fail("The item " + item + " " + item.getClass() + " is not in the combo box " + combo + " items are " + Utility.listToString(comboItems) + " " + (comboItems.isEmpty() ? "" : comboItems.get(0).getClass()));

        clickOn(combo);

        if(index > indexSel)
            for(int i = indexSel; i < index; i++)
                push(KeyCode.DOWN);
        else if(index < indexSel)
            for(int i = indexSel; i > index; i--)
                push(KeyCode.UP);

        push(KeyCode.ENTER);
    }
}
