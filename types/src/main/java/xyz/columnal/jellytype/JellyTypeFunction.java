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
import xyz.columnal.typeExp.TypeExp;
import xyz.columnal.typeExp.units.MutUnitVar;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.Utility;

import java.util.Objects;
import java.util.function.Consumer;

class JellyTypeFunction extends JellyType
{
    private final ImmutableList<@Recorded JellyType> params;
    private final @Recorded JellyType result;

    JellyTypeFunction(ImmutableList<@Recorded JellyType> params, @Recorded JellyType result)
    {
        this.params = params;
        this.result = result;
    }

    @Override
    public TypeExp makeTypeExp(ImmutableMap<String, Either<MutUnitVar, MutVar>> typeVariables) throws InternalException
    {
        return TypeExp.function(null, Utility.mapListInt(params, p -> p.makeTypeExp(typeVariables)), result.makeTypeExp(typeVariables));
    }

    @Override
    public DataType makeDataType(ImmutableMap<String, Either<Unit, DataType>> typeVariables, TypeManager mgr) throws InternalException, UnknownTypeException, TaggedInstantiationException
    {
        ImmutableList.Builder<DataType> paramTypes = ImmutableList.builderWithExpectedSize(params.size());

        for (@Recorded JellyType param : params)
        {
            paramTypes.add(param.makeDataType(typeVariables, mgr));
        }
        
        return DataType.function(paramTypes.build(), result.makeDataType(typeVariables, mgr));
    }

    @Override
    public void forNestedTagged(Consumer<TypeId> nestedTagged)
    {
        params.forEach(p -> p.forNestedTagged(nestedTagged));
        result.forNestedTagged(nestedTagged);
    }

    @Override
    public <R, E extends Throwable> R apply(JellyTypeVisitorEx<R, E> visitor) throws InternalException, E
    {
        return visitor.function(params, result);
    }

    @Override
    public void save(OutputBuilder output)
    {
        output.raw("((");
        for (int i = 0; i < params.size(); i++)
        {
            params.get(i).save(output);
            if (i > 0)
                output.raw(", ");
        }
        output.raw(") -> ");
        result.save(output);
        output.raw(")");
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JellyTypeFunction that = (JellyTypeFunction) o;
        return Objects.equals(params, that.params) &&
            Objects.equals(result, that.result);
    }

    @Override
    public int hashCode()
    {

        return Objects.hash(params, result);
    }
}
