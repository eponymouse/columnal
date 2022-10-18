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

package xyz.columnal.data;

import annotation.qual.Value;
import org.checkerframework.checker.nullness.qual.NonNull;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.id.ColumnId;
import xyz.columnal.utility.function.simulation.SimulationRunnable;

/**
 * Created by neil on 29/05/2017.
 */
public abstract class EditableColumn extends Column
{
    protected EditableColumn(RecordSet recordSet, ColumnId name)
    {
        super(recordSet, name);
    }

    // Returns a revert operation
    @OnThread(Tag.Simulation)
    public abstract SimulationRunnable insertRows(int index, int count) throws InternalException, UserException;

    // Returns a revert operation
    @OnThread(Tag.Simulation)
    public abstract SimulationRunnable removeRows(int index, int count) throws InternalException, UserException;

    @OnThread(Tag.Any)
    public boolean isEditable()
    {
        return true;
    }

    @OnThread(Tag.Any)
    public abstract @NonNull @Value Object getDefaultValue();

    @Override
    public @OnThread(Tag.Any) AlteredState getAlteredState()
    {
        // If we're editable, we must be new:
        return AlteredState.OVERWRITTEN;
    }
}
