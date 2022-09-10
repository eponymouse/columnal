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

import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.CellPosition;
import xyz.columnal.id.TableId;
import xyz.columnal.data.TableManager;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Workers;
import xyz.columnal.utility.Workers.Priority;

public abstract class SimpleColumnOperation extends ColumnOperation
{
    private final TableManager tableManager;
    private final @Nullable TableId toRightOf;
    
    protected SimpleColumnOperation(TableManager tableManager, @Nullable TableId toRightOf, @LocalizableKey String nameKey)
    {
        super(nameKey);
        this.tableManager = tableManager;
        this.toRightOf = toRightOf;
    }

    // insertPosition is where you could put a new table.
    @OnThread(Tag.Simulation)
    public abstract void execute(CellPosition insertPosition);

    @Override
    public @OnThread(Tag.FXPlatform) void executeFX()
    {
        CellPosition insertPosition = tableManager.getNextInsertPosition(toRightOf);
        Workers.onWorkerThread(nameKey, Priority.SAVE, () -> execute(insertPosition));
    }

    @Override
    protected String[] getStyleClasses()
    {
        return new String[] {"recipe-menu-item"};
    }
}
