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

package xyz.columnal.gui.stable;

import javafx.scene.control.MenuItem;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Workers;
import xyz.columnal.utility.Workers.Priority;
import xyz.columnal.utility.gui.GUI;

/**
 * Created by neil on 29/05/2017.
 */
abstract class RowOperation
{
    @OnThread(Tag.Any)
    private final @LocalizableKey String nameKey;

    protected RowOperation(@LocalizableKey String nameKey)
    {
        this.nameKey = nameKey;
    }

    @OnThread(Tag.Simulation)
    public abstract void execute();

    @OnThread(Tag.FXPlatform)
    public MenuItem makeMenuItem()
    {
        return GUI.menuItem(nameKey, () -> Workers.onWorkerThread(nameKey, Priority.SAVE, this::execute));
    }
}
