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

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.Value;
import com.google.common.collect.ImmutableMap;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataType.SpecificDataTypeVisitor;
import xyz.columnal.data.datatype.DataTypeValue;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.id.ColumnId;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.simulation.SimulationRunnable;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Utility.Record;

import java.util.List;
import java.util.stream.Stream;

/**
 * Created by neil on 31/10/2016.
 */
public class MemoryRecordColumn extends EditableColumn
{
    private final RecordColumnStorage storage;
    @OnThread(Tag.Any)
    private final @Value Record defaultValue;

    public MemoryRecordColumn(RecordSet recordSet, ColumnId title, ImmutableMap<@ExpressionIdentifier String, DataType> dataTypes, @Value Record defaultValue) throws InternalException
    {
        super(recordSet, title);
        this.defaultValue = defaultValue;
        this.storage = new RecordColumnStorage(dataTypes, true);
    }

    public MemoryRecordColumn(RecordSet recordSet, ColumnId title, ImmutableMap<@ExpressionIdentifier String, DataType> dataTypes, List<Either<String, @Value Record>> values, @Value Record defaultValue) throws InternalException
    {
        this(recordSet, title, dataTypes, defaultValue);
        addAllValue(storage, values);
    }

    private static void addAllValue(RecordColumnStorage storage, List<Either<String, @Value Record>> values) throws InternalException
    {
        storage.addAll(values.stream());
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
        MemoryRecordColumn shrunk = new MemoryRecordColumn(rs, getName(), storage.getType().getType().apply(new SpecificDataTypeVisitor<ImmutableMap<@ExpressionIdentifier String, DataType>>() {
            @Override
            public ImmutableMap<@ExpressionIdentifier String, DataType> record(ImmutableMap<@ExpressionIdentifier String, DataType> fields) throws InternalException, InternalException
            {
                return fields;
            }
        }), defaultValue);
        shrunk.storage.addAll(storage.getAllCollapsed(0, shrunkLength).stream());
        return shrunk;
    }

    public void add(Either<String, @Value Record> tuple) throws InternalException
    {
        storage.addAll(Stream.of(tuple));
    }


    @Override
    public @OnThread(Tag.Simulation) SimulationRunnable insertRows(int index, int count) throws InternalException, UserException
    {
        return storage.insertRows(index, Utility.replicate(count, Either.<String, @Value Record>right(defaultValue)));
    }

    @Override
    public @OnThread(Tag.Simulation) SimulationRunnable removeRows(int index, int count) throws InternalException, UserException
    {
        return storage.removeRows(index, count);
    }

    @Override
    @OnThread(Tag.Any)
    public @Value Record getDefaultValue()
    {
        return defaultValue;
    }
}
