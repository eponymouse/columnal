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

import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.utility.function.simulation.SimulationFunction;
import xyz.columnal.utility.function.simulation.SimulationFunctionInt;

import java.util.List;

public abstract class TransformationRecordSet extends RecordSet
{
    public TransformationRecordSet()
    {
        super();
    }

    public TransformationRecordSet(List<SimulationFunction<RecordSet, Column>> columns) throws InternalException, UserException
    {
        super(columns);
    }

    public void buildColumn(SimulationFunctionInt<RecordSet, Column> makeColumn) throws InternalException, UserException
    {
        columns.add(makeColumn.apply(this));
        if (columns.stream().map(Column::getName).distinct().count() != columns.size())
        {
            throw new UserException("Duplicate column names found");
        }
    }
}
