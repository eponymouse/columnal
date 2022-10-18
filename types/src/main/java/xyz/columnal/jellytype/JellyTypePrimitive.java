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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import xyz.columnal.log.Log;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataType.DataTypeVisitorEx;
import xyz.columnal.data.datatype.DataType.DateTimeInfo;
import xyz.columnal.data.datatype.DataType.TagType;
import xyz.columnal.data.datatype.NumberInfo;
import xyz.columnal.data.datatype.TypeId;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.loadsave.OutputBuilder;
import xyz.columnal.typeExp.MutVar;
import xyz.columnal.typeExp.TypeExp;
import xyz.columnal.typeExp.units.MutUnitVar;
import xyz.columnal.utility.adt.Either;

import java.util.Objects;
import java.util.function.Consumer;

class JellyTypePrimitive extends JellyType
{
    private final DataType dataType;

    private JellyTypePrimitive(DataType dataType)
    {
        this.dataType = dataType;
    }
    
    public static JellyTypePrimitive bool()
    {
        return new JellyTypePrimitive(DataType.BOOLEAN);
    }

    public static JellyTypePrimitive text()
    {
        return new JellyTypePrimitive(DataType.TEXT);
    }

    public static JellyTypePrimitive date(DateTimeInfo dateTimeInfo)
    {
        return new JellyTypePrimitive(DataType.date(dateTimeInfo));
    }

    @Override
    public TypeExp makeTypeExp(ImmutableMap<String, Either<MutUnitVar, MutVar>> typeVariables) throws InternalException
    {
        return TypeExp.fromDataType(null, dataType);
    }

    @Override
    public DataType makeDataType(ImmutableMap<String, Either<Unit, DataType>> typeVariables, TypeManager mgr) throws InternalException
    {
        return dataType;
    }

    @Override
    public void save(OutputBuilder output)
    {
        try
        {
            dataType.save(output);
        }
        catch (InternalException e)
        {
            Log.log(e);
            // Not sure what more we can do here: write rest and
            // hope user can fix it later, I guess.
        }
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JellyTypePrimitive that = (JellyTypePrimitive) o;
        return Objects.equals(dataType, that.dataType);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(dataType);
    }

    @Override
    public void forNestedTagged(Consumer<TypeId> nestedTagged)
    {
        // No nested tagged types
    }

    @Override
    public <R, E extends Throwable> R apply(JellyTypeVisitorEx<R, E> visitor) throws InternalException, E
    {
        return dataType.apply(new DataTypeVisitorEx<R, E>()
        {
            @Override
            public R text() throws InternalException, E
            {
                return visitor.text();
            }

            @Override
            public R date(DateTimeInfo dateTimeInfo) throws InternalException, E
            {
                return visitor.date(dateTimeInfo);
            }

            @Override
            public R bool() throws InternalException, E
            {
                return visitor.bool();
            }

            @Override
            public R number(NumberInfo numberInfo) throws InternalException, E
            {
                return _throw();
            }

            @Override
            public R tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, E
            {
                return _throw();
            }

            @Override
            public R record(ImmutableMap<@ExpressionIdentifier String, DataType> fields) throws InternalException, E
            {
                return _throw();
            }

            @Override
            public R array(DataType inner) throws InternalException, E
            {
                return _throw();
            }

            @Override
            public R function(ImmutableList<DataType> argTypes, DataType resultType) throws InternalException, E
            {
                return _throw();
            }

            private R _throw() throws InternalException
            {
                throw new InternalException("Impossible type " + dataType + " found in JellyTypePrimitive");
            }
        });
    }

    @Override
    public String toString()
    {
        return "JellyTypePrimitive{" +
                "dataType=" + dataType +
                '}';
    }
}
