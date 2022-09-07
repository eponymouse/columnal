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

package xyz.columnal.gui.grid;

import annotation.units.AbsColIndex;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.dataflow.qual.Pure;

import java.util.HashMap;
import java.util.Map;

class GridWithColumnWidths
{
    static final double DEFAULT_COLUMN_WIDTH = 100;
    static final double fixedFirstColumnWidth = 20;

    protected final Map<@AbsColIndex Integer, Double> customisedColumnWidths = new HashMap<>();
    @Pure
    public final double getColumnWidth(@UnknownInitialization(GridWithColumnWidths.class) GridWithColumnWidths this, int columnIndex)
    {
        if (columnIndex == 0)
            return fixedFirstColumnWidth;
        else
            return customisedColumnWidths.getOrDefault(columnIndex, DEFAULT_COLUMN_WIDTH);
    }
}
