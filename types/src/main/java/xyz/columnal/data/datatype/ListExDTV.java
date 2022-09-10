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

package xyz.columnal.data.datatype;

import annotation.qual.Value;
import xyz.columnal.data.Column;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Utility.ListEx;

/**
 * A ListEx wrapped around a column
 */
@OnThread(Tag.Simulation)
public class ListExDTV extends ListEx
{
    private final int length;
    private final DataTypeValue columnType;

    public ListExDTV(Column column) throws UserException, InternalException
    {
        this.length = column.getLength();
        this.columnType = column.getType();
    }
    
    public ListExDTV(int length, DataTypeValue values)
    {
        this.length = length;
        this.columnType = values;
    }

    @Override
    public int size() throws InternalException, UserException
    {
        return length;
    }

    @Override
    public @Value Object get(int index) throws InternalException, UserException
    {
        return columnType.getCollapsed(index);
    }
}
