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
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataType.SpecificDataTypeVisitor;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.DataTypeValue;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.id.ColumnId;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.simulation.SimulationRunnable;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Utility.ListEx;

import java.util.List;
import java.util.stream.Stream;

/**
 * Created by neil on 31/10/2016.
 */
public class MemoryArrayColumn extends EditableColumn
{
    private final ArrayColumnStorage storage;
    private final @Value ListEx defaultValue;

    public MemoryArrayColumn(RecordSet recordSet, ColumnId title, DataType inner, List<Either<String, ListEx>> values, ListEx defaultValue) throws InternalException
    {
        super(recordSet, title);
        this.defaultValue = DataTypeUtility.value(defaultValue);
        this.storage = new ArrayColumnStorage(inner, null, true);
        this.storage.addAll(values.stream());
    }

    @Override
    @OnThread(Tag.Any)
    public synchronized DataTypeValue getType()
    {
        return storage.getType();
    }

    @Override
    public Column _test_shrink(RecordSet rs, int shrunkLength) throws InternalException, UserException
    {
        MemoryArrayColumn shrunk = new MemoryArrayColumn(rs, getName(), storage.getType().getType().apply(new SpecificDataTypeVisitor<DataType>() {
            @Override
            public DataType array(DataType inner) throws InternalException
            {
                return inner;
            }
        }), storage.getAllCollapsed(0, shrunkLength), defaultValue);
        return shrunk;
    }

    public void add(Either<String, ListEx> listEx) throws InternalException
    {
        storage.addAll(Stream.of(listEx));
    }


    @Override
    public @OnThread(Tag.Simulation) SimulationRunnable insertRows(int index, int count) throws InternalException, UserException
    {
        return storage.insertRows(index, Utility.<Either<String, ListEx>>replicate(count, Either.<String, ListEx>right(defaultValue)));
    }

    @Override
    public @OnThread(Tag.Simulation) SimulationRunnable removeRows(int index, int count) throws InternalException, UserException
    {
        return storage.removeRows(index, count);
    }

    @Override
    @OnThread(Tag.Any)
    public @Value Object getDefaultValue()
    {
        return defaultValue;
    }
}
