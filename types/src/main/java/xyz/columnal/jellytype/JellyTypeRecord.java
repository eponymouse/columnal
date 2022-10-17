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

package xyz.columnal.jellytype;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.TaggedTypeDefinition.TaggedInstantiationException;
import xyz.columnal.data.datatype.TypeId;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.loadsave.OutputBuilder;
import xyz.columnal.typeExp.MutVar;
import xyz.columnal.typeExp.RecordTypeExp;
import xyz.columnal.typeExp.TypeExp;
import xyz.columnal.typeExp.units.MutUnitVar;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.Utility;

import java.util.Comparator;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.Consumer;

public class JellyTypeRecord extends JellyType
{
    public static class Field
    {
        private final @Recorded JellyType type;
        private final boolean required;

        public Field(@Recorded JellyType type, boolean required)
        {
            this.type = type;
            this.required = required;
        }

        @Override
        public boolean equals(@Nullable Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Field field = (Field) o;
            return required == field.required &&
                type.equals(field.type);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(type, required);
        }

        public DataType makeDataType(ImmutableMap<String, Either<Unit, DataType>> typeVariables, TypeManager mgr) throws InternalException, UnknownTypeException, TaggedInstantiationException
        {
            DataType core = type.makeDataType(typeVariables, mgr);
            if (required)
                return core;
            else
                return mgr.getMaybeType().instantiate(ImmutableList.of(Either.<Unit, DataType>right(core)), mgr);
        }

        public @Recorded JellyType getJellyType()
        {
            return type;
        }
    }
    
    private final ImmutableMap<@ExpressionIdentifier String, Field> fields;
    private final boolean complete;

    public JellyTypeRecord(ImmutableMap<@ExpressionIdentifier String, Field> fields, boolean complete)
    {
        this.fields = fields;
        this.complete = complete;
    }

    @Override
    public TypeExp makeTypeExp(ImmutableMap<String, Either<MutUnitVar, MutVar>> typeVariables) throws InternalException
    {
        return new RecordTypeExp(null, Utility.mapValuesInt(fields, f -> f.type.makeTypeExp(typeVariables)), complete);
    }

    @Override
    public DataType makeDataType(ImmutableMap<String, Either<Unit, DataType>> typeVariables, TypeManager mgr) throws InternalException, UnknownTypeException, TaggedInstantiationException
    {
        ImmutableMap.Builder<@ExpressionIdentifier String, DataType> types = ImmutableMap.builderWithExpectedSize(fields.size());
        for (Entry<@ExpressionIdentifier String, Field> entry : fields.entrySet())
        {
            types.put(entry.getKey(), entry.getValue().makeDataType(typeVariables, mgr));
        }
        return DataType.record(types.build());
    }

    @Override
    public void save(OutputBuilder output)
    {
        output.raw("(");
        boolean first = true;
        for (Entry<@ExpressionIdentifier String, Field> entry : Utility.iterableStream(fields.entrySet().stream().sorted(Comparator.comparing(e -> e.getKey()))))
        {
            if (!first)
                output.raw(",");
            first = false;
            output.expId(entry.getKey()).raw(entry.getValue().required ? ":" : "?:");
            entry.getValue().type.save(output);
        }
        output.raw(")");
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JellyTypeRecord that = (JellyTypeRecord) o;
        return complete == that.complete &&
            fields.equals(that.fields);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(fields, complete);
    }

    @Override
    public void forNestedTagged(Consumer<TypeId> nestedTagged)
    {
        for (Field value : fields.values())
        {
            value.type.forNestedTagged(nestedTagged);
        }
    }

    @Override
    public <R, E extends Throwable> R apply(JellyTypeVisitorEx<R, E> visitor) throws InternalException, E
    {
        return visitor.record(fields, complete);
    }
}
