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
import com.google.common.collect.ImmutableList;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataType.SpecificDataTypeVisitor;
import xyz.columnal.data.datatype.DataType.TagType;
import xyz.columnal.data.datatype.DataTypeValue;
import xyz.columnal.data.datatype.TypeId;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.id.ColumnId;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.simulation.SimulationRunnable;
import xyz.columnal.utility.TaggedValue;
import xyz.columnal.utility.Utility;

import java.util.List;
import java.util.stream.Stream;

/**
 * Created by neil on 30/11/2016.
 */
public class MemoryTaggedColumn extends EditableColumn
{
    private final TaggedColumnStorage storage;
    private final TypeId typeName;
    private final TaggedValue defaultValue;
    private final ImmutableList<Either<Unit, DataType>> typeVars;

    public MemoryTaggedColumn(RecordSet rs, ColumnId title, TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, List<TagType<DataType>> tags, List<Either<String, TaggedValue>> values, TaggedValue defaultValue) throws InternalException
    {
        super(rs, title);
        this.typeName = typeName;
        this.typeVars = typeVars;
        this.defaultValue = defaultValue;
        this.storage = new TaggedColumnStorage(typeName, typeVars, tags, true);
        this.storage.addAll(values.stream());
    }

    @Override
    @OnThread(Tag.Any)
    public synchronized DataTypeValue getType() throws InternalException
    {
        return storage.getType();
    }

    @Override
    public Column _test_shrink(RecordSet rs, int shrunkLength) throws InternalException, UserException
    {
        List<TagType<DataType>> tags = getType().getType().apply(new SpecificDataTypeVisitor<List<TagType<DataType>>>()
        {
            @Override
            public List<TagType<DataType>> tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException
            {
                return tags;
            }
        });
        return new MemoryTaggedColumn(rs, getName(), typeName, typeVars, tags, storage.getShrunk(shrunkLength), defaultValue);
    }

    public void add(Either<String, TaggedValue> taggedValue) throws InternalException
    {
        storage.addAll(Stream.of(taggedValue));
    }


    @SuppressWarnings("valuetype") // Not 100% sure why this is needed
    @Override
    public @OnThread(Tag.Simulation) SimulationRunnable insertRows(int index, int count) throws InternalException, UserException
    {
        return storage.insertRows(index, Utility.replicate(count, Either.<String, TaggedValue>right(defaultValue)));
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
