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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.checkerframework.checker.nullness.qual.Nullable;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.data.ColumnStorage.BeforeGet;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataType.DataTypeVisitor;
import xyz.columnal.data.datatype.DataType.DataTypeVisitorEx;
import xyz.columnal.data.datatype.DataType.DateTimeInfo;
import xyz.columnal.data.datatype.DataType.TagType;
import xyz.columnal.data.datatype.DataTypeValue;
import xyz.columnal.data.datatype.NumberInfo;
import xyz.columnal.data.datatype.TypeId;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.id.ColumnId;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.ExFunction;
import xyz.columnal.utility.function.FunctionInt;
import xyz.columnal.utility.function.simulation.SimulationFunction;
import xyz.columnal.utility.TaggedValue;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Utility.ListEx;
import xyz.columnal.utility.Utility.Record;

import java.time.temporal.TemporalAccessor;
import java.util.Collections;
import java.util.List;

public class ColumnUtility
{
    @OnThread(Tag.Simulation)
    public static Column makeCalculatedColumn(DataType dataType, RecordSet rs, ColumnId name, ExFunction<Integer, @Value Object> getItem, FunctionInt<DataTypeValue, DataTypeValue> addManualEdit) throws InternalException
    {
        return dataType.apply(new DataTypeVisitorEx<Column, InternalException>()
        {
            @SuppressWarnings("valuetype")
            private <T> @Value T castTo(Class<T> cls, @Value Object value) throws InternalException
            {
                if (!cls.isAssignableFrom(value.getClass()))
                    throw new InternalException("Type inconsistency: should be " + cls + " but is " + value.getClass() + " for column: " + name);
                return cls.cast(value);
            }

            @Override
            @OnThread(Tag.Simulation)
            public Column number(NumberInfo displayInfo) throws InternalException
            {
                return new CachedCalculatedColumn<Number, NumericColumnStorage>(rs, name, (BeforeGet<NumericColumnStorage> g) -> new NumericColumnStorage(displayInfo, g, false), i -> {
                    return castTo(Number.class, getItem.apply(i));
                }, addManualEdit);
            }

            @Override
            @OnThread(Tag.Simulation)
            public Column text() throws InternalException
            {
                return new CachedCalculatedColumn<String, StringColumnStorage>(rs, name, (BeforeGet<StringColumnStorage> g) -> new StringColumnStorage(g, false), i -> {
                    return castTo(String.class, getItem.apply(i));
                }, addManualEdit);
            }

            @Override
            @OnThread(Tag.Simulation)
            public Column date(DateTimeInfo dateTimeInfo) throws InternalException
            {
                return new CachedCalculatedColumn<TemporalAccessor, TemporalColumnStorage>(rs, name, (BeforeGet<TemporalColumnStorage> g) -> new TemporalColumnStorage(dateTimeInfo, g, false), i -> {
                    return castTo(TemporalAccessor.class, getItem.apply(i));
                }, addManualEdit);
            }

            @Override
            @OnThread(Tag.Simulation)
            public Column bool() throws InternalException
            {
                return new CachedCalculatedColumn<Boolean, BooleanColumnStorage>(rs, name, (BeforeGet<BooleanColumnStorage> g) -> new BooleanColumnStorage(g, false), i -> {
                    return castTo(Boolean.class, getItem.apply(i));
                }, addManualEdit);
            }

            @Override
            @OnThread(Tag.Simulation)
            public Column tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException
            {
                return new CachedCalculatedColumn<@Value TaggedValue, TaggedColumnStorage>(rs, name, (BeforeGet<TaggedColumnStorage> g) -> new TaggedColumnStorage(typeName, typeVars, tags, g, false), i -> {
                    return castTo(TaggedValue.class, getItem.apply(i));
                }, addManualEdit);
            }

            @Override
            @OnThread(Tag.Simulation)
            public Column record(ImmutableMap<@ExpressionIdentifier String, DataType> fields) throws InternalException
            {
                return new CachedCalculatedColumn<@Value Record, RecordColumnStorage>(rs, name, (BeforeGet<RecordColumnStorage> g) -> new RecordColumnStorage(fields, g, false), i -> {
                    return castTo(Record.class, getItem.apply(i));
                }, addManualEdit);
            }

            @Override
            @OnThread(Tag.Simulation)
            public Column array(DataType inner) throws InternalException
            {
                return new CachedCalculatedColumn<ListEx, ArrayColumnStorage>(rs, name, (BeforeGet<ArrayColumnStorage> g) -> new ArrayColumnStorage(inner, g, false), i -> {
                    return castTo(ListEx.class, getItem.apply(i));
                }, addManualEdit);
            }
        });
    }


    @OnThread(Tag.Simulation)
    public static SimulationFunction<RecordSet, EditableColumn> makeImmediateColumn(DataType dataType, ColumnId columnId, List<Either<String, @Value Object>> value, @Value Object defaultValue) throws InternalException, UserException
    {
        return dataType.apply(new DataTypeVisitor<SimulationFunction<RecordSet, EditableColumn>>()
        {
            @SuppressWarnings("valuetype")
            private <T> List<Either<String, T>> listValue(List<Either<String, @Value Object>> values, FunctionInt<@Value Object, @Value T> applyValue) throws InternalException
            {
                return Utility.mapListInt(values, x -> x.<@Value T>mapInt(applyValue));
            }

            @Override
            @OnThread(Tag.Simulation)
            public SimulationFunction<RecordSet, EditableColumn> number(NumberInfo displayInfo) throws InternalException, UserException
            {
                return rs -> new MemoryNumericColumn(rs, columnId, displayInfo, listValue(value, Utility::valueNumber), Utility.cast(defaultValue, Number.class));
            }

            @Override
            @OnThread(Tag.Simulation)
            public SimulationFunction<RecordSet, EditableColumn> text() throws InternalException, UserException
            {
                return rs -> new MemoryStringColumn(rs, columnId, listValue(value, Utility::valueString), Utility.cast(defaultValue, String.class));
            }

            @Override
            @OnThread(Tag.Simulation)
            public SimulationFunction<RecordSet, EditableColumn> date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
            {
                return rs -> new MemoryTemporalColumn(rs, columnId, dateTimeInfo, listValue(value, Utility::valueTemporal), Utility.cast(defaultValue, TemporalAccessor.class));
            }

            @Override
            @OnThread(Tag.Simulation)
            public SimulationFunction<RecordSet, EditableColumn> bool() throws InternalException, UserException
            {
                return rs -> new MemoryBooleanColumn(rs, columnId, listValue(value, Utility::valueBoolean), Utility.cast(defaultValue, Boolean.class));
            }

            @Override
            @OnThread(Tag.Simulation)
            public SimulationFunction<RecordSet, EditableColumn> tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, UserException
            {
                return rs -> new MemoryTaggedColumn(rs, columnId, typeName, typeVars, tags, listValue(value, Utility::valueTagged), Utility.cast(defaultValue, TaggedValue.class));
            }

            @Override
            @OnThread(Tag.Simulation)
            public SimulationFunction<RecordSet, EditableColumn> record(ImmutableMap<@ExpressionIdentifier String, DataType> fields) throws InternalException, UserException
            {
                return rs -> new MemoryRecordColumn(rs, columnId, fields, this.<@Value Record>listValue(value, (@Value Object t) -> Utility.valueRecord(t)), Utility.valueRecord(defaultValue));
            }

            @Override
            @OnThread(Tag.Simulation)
            public SimulationFunction<RecordSet, EditableColumn> array(@Nullable DataType inner) throws InternalException, UserException
            {
                if (inner == null)
                    throw new UserException("Cannot create column with empty array type");
                DataType innerFinal = inner;
                return rs -> new MemoryArrayColumn(rs, columnId, innerFinal, listValue(value, Utility::valueList), Utility.cast(defaultValue, ListEx.class));
            }
        });
    }

    @OnThread(Tag.Simulation)
    public static ColumnMaker<?, ?> makeImmediateColumn(DataType dataType, ColumnId columnId, @Value Object defaultValue) throws InternalException, UserException
    {
        return dataType.apply(new DataTypeVisitor<ColumnMaker<?, ?>>()
        {
            @Override
            @OnThread(Tag.Simulation)
            public ColumnMaker<?, ?> number(NumberInfo displayInfo) throws InternalException, UserException
            {
                return new ColumnMaker<MemoryNumericColumn, Number>(defaultValue, Number.class, (rs, defaultValue) -> new MemoryNumericColumn(rs, columnId, displayInfo, Collections.emptyList(), defaultValue), (c, n) -> c.add(n), p -> DataType.loadNumber(p), p -> DataType.loadNumber(p));
            }

            @Override
            @OnThread(Tag.Simulation)
            public ColumnMaker<?, ?> text() throws InternalException, UserException
            {
                return new ColumnMaker<MemoryStringColumn, String>(defaultValue, String.class, (rs, defaultValue) -> new MemoryStringColumn(rs, columnId, Collections.emptyList(), defaultValue), (c, s) -> c.add(s), p -> DataType.loadString(p), p -> DataType.loadString(p));
            }

            @Override
            @OnThread(Tag.Simulation)
            public ColumnMaker<?, ?> date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
            {
                return new ColumnMaker<MemoryTemporalColumn, TemporalAccessor>(defaultValue, TemporalAccessor.class, (rs, defaultValue) -> new MemoryTemporalColumn(rs, columnId, dateTimeInfo, Collections.emptyList(), defaultValue), (c, t) -> c.add(t), p -> dateTimeInfo.parse(p), p -> dateTimeInfo.parse(p));
            }

            @Override
            @OnThread(Tag.Simulation)
            public ColumnMaker<?, ?> bool() throws InternalException, UserException
            {
                return new ColumnMaker<MemoryBooleanColumn, Boolean>(defaultValue, Boolean.class, (rs, defaultValue) -> new MemoryBooleanColumn(rs, columnId, Collections.emptyList(), defaultValue), (c, b) -> c.add(b), p -> DataType.loadBool(p), p -> DataType.loadBool(p));
            }

            @Override
            @OnThread(Tag.Simulation)
            public ColumnMaker<?, ?> tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, UserException
            {
                return new ColumnMaker<MemoryTaggedColumn, TaggedValue>(defaultValue, TaggedValue.class, (rs, defaultValue) -> new MemoryTaggedColumn(rs, columnId, typeName, typeVars, tags, Collections.emptyList(), defaultValue), (c, t) -> c.add(t), p -> DataType.loadTaggedValue(tags, p), p -> DataType.loadTaggedValue(tags, p));
            }

            @Override
            @OnThread(Tag.Simulation)
            public ColumnMaker<?, ?> record(ImmutableMap<@ExpressionIdentifier String, DataType> fields) throws InternalException, UserException
            {
                return new ColumnMaker<MemoryRecordColumn, @Value Record>(defaultValue, (Class<@Value Record>)Record.class, (RecordSet rs, @Value Record defaultValue) -> new MemoryRecordColumn(rs, columnId, fields, defaultValue), (MemoryRecordColumn c, Either<String, @Value Record> t) -> c.add(t), p -> DataType.loadRecord(fields, p, false), p -> DataType.loadRecord(fields, p, false));
            }

            @Override
            @OnThread(Tag.Simulation)
            public ColumnMaker<?, ?> array(@Nullable DataType inner) throws InternalException, UserException
            {
                if (inner == null)
                    throw new UserException("Cannot have column with type of empty array");

                DataType innerFinal = inner;
                return new ColumnMaker<MemoryArrayColumn, ListEx>(defaultValue, ListEx.class, (rs, defaultValue) -> new MemoryArrayColumn(rs, columnId, innerFinal, Collections.emptyList(), defaultValue), (c, v) -> c.add(v), p -> DataType.loadArray(innerFinal, p), p -> DataType.loadArray(innerFinal, p));
            }
        });
    }

    @OnThread(Tag.Simulation)
    @SuppressWarnings("unchecked")
    public static ColumnStorage<?> makeColumnStorage(final DataType inner, ColumnStorage.@Nullable BeforeGet<?> beforeGet, boolean isImmediateData) throws InternalException
    {
        return inner.apply(new DataTypeVisitorEx<ColumnStorage<?>, InternalException>()
        {
            @Override
            @OnThread(Tag.Simulation)
            public ColumnStorage<?> number(NumberInfo displayInfo) throws InternalException
            {
                return new NumericColumnStorage(displayInfo, (BeforeGet<NumericColumnStorage>)beforeGet, isImmediateData);
            }

            @Override
            @OnThread(Tag.Simulation)
            public ColumnStorage<?> bool() throws InternalException
            {
                return new BooleanColumnStorage((BeforeGet<BooleanColumnStorage>)beforeGet, isImmediateData);
            }

            @Override
            @OnThread(Tag.Simulation)
            public ColumnStorage<?> text() throws InternalException
            {
                return new StringColumnStorage((BeforeGet<StringColumnStorage>)beforeGet, isImmediateData);
            }

            @Override
            @OnThread(Tag.Simulation)
            public ColumnStorage<?> date(DateTimeInfo dateTimeInfo) throws InternalException
            {
                return new TemporalColumnStorage(dateTimeInfo, (BeforeGet<TemporalColumnStorage>)beforeGet, isImmediateData);
            }

            @Override
            @OnThread(Tag.Simulation)
            public ColumnStorage<?> tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException
            {
                return new TaggedColumnStorage(typeName, typeVars, tags, (BeforeGet<TaggedColumnStorage>)beforeGet, isImmediateData);
            }

            @Override
            @OnThread(Tag.Simulation)
            public ColumnStorage<?> record(ImmutableMap<@ExpressionIdentifier String, DataType> fields) throws InternalException, InternalException
            {
                return new RecordColumnStorage(fields, beforeGet, isImmediateData);
            }

            @Override
            @OnThread(Tag.Simulation)
            public ColumnStorage<?> array(DataType inner) throws InternalException
            {
                return new ArrayColumnStorage(inner, (BeforeGet<ArrayColumnStorage>)beforeGet, isImmediateData);
            }
        });
    }

}
