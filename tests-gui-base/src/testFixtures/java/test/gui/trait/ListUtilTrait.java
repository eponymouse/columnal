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

import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import org.testjavafx.FxRobotInterface;
import test.gui.TFXUtil;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Utility;

import java.util.OptionalInt;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Created by neil on 11/06/2017.
 */
public interface ListUtilTrait extends FxRobotInterface
{
    @OnThread(Tag.Any)
    default <T> void selectGivenListViewItem(final ListView<T> list, final Predicate<T> findItem) {
        OptionalInt firstIndex = TFXUtil.fx(() -> Utility.findFirstIndex(list.getItems(), findItem));
        assertTrue("Not found item satisfying predicate", firstIndex.isPresent());
        final int index = firstIndex.getAsInt();

        clickOn(list);
        push(KeyCode.SPACE);
        sleep(100);
        // If nothing selected, selection will begin when you hit a key:
        Supplier<Integer> cur = () -> TFXUtil.fx(() -> list.getSelectionModel().getSelectedIndex());
        
        while (cur.get() < index)
            push(KeyCode.DOWN);
        while (cur.get() > index)
            push(KeyCode.UP);
        
        assertEquals(index, (int)cur.get());
    }
}
