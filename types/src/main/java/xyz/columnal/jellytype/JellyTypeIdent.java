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
import xyz.columnal.data.datatype.DataType.DateTimeInfo;
import xyz.columnal.data.datatype.DataType.DateTimeInfo.DateTimeType;
import xyz.columnal.data.datatype.TaggedTypeDefinition;
import xyz.columnal.data.datatype.TaggedTypeDefinition.TaggedInstantiationException;
import xyz.columnal.data.datatype.TypeId;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.loadsave.OutputBuilder;
import xyz.columnal.typeExp.MutVar;
import xyz.columnal.typeExp.TypeCons;
import xyz.columnal.typeExp.TypeExp;
import xyz.columnal.typeExp.units.MutUnitVar;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.Utility;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * A plain ident, could be
 * 
 * 
 * - A tagged type without arguments
 * - A type variable, like in a function definition or tag definition
 */
class JellyTypeIdent extends JellyType
{
    private final @ExpressionIdentifier String name;

    JellyTypeIdent(@ExpressionIdentifier String name)
    {
        this.name = name;
    }
    
    @Override
    public TypeExp makeTypeExp(ImmutableMap<String, Either<MutUnitVar, MutVar>> typeVariables) throws InternalException
    {
        Either<MutUnitVar, MutVar> var = typeVariables.get(name);
        if (var != null)
            return var.getRight("Variable " + name + " should be type variable but was unit variable");
        
        return new TypeCons(null, name, ImmutableList.of(), TypeExp.ALL_TYPE_CLASSES);
        
    }

    @Override
    public DataType makeDataType(@Recorded JellyTypeIdent this, ImmutableMap<String, Either<Unit, DataType>> typeVariables, TypeManager mgr) throws InternalException, UnknownTypeException, TaggedInstantiationException
    {
        Either<Unit, DataType> var = typeVariables.get(name);
        if (var != null)
            return var.getRight("Variable " + name + " should be type variable but was unit variable");
        DataType dataType = mgr.lookupType(new TypeId(name), ImmutableList.of());
        if (dataType != null)
            return dataType;
        @SuppressWarnings("identifier") // Due to DataType.toString
        ImmutableList<JellyType> fixes = Utility.findAlternatives(name, streamKnownTypes(mgr), t -> t.either(ttd -> Stream.<String>of(ttd.getTaggedTypeName().getRaw()), dts -> Stream.<String>concat(Stream.<String>of(dts.getFirst().toString()), dts.getSecond().stream()))).map(t -> new JellyTypeIdent(t.either(ttd -> ttd.getTaggedTypeName().getRaw(), dts -> dts.getFirst().toString()))).collect(ImmutableList.<JellyType>toImmutableList());
        throw new UnknownTypeException("Unknown type or type variable: " + name, this, fixes);
        
    }

    private static Stream<Either<TaggedTypeDefinition, Pair<DataType, ImmutableList<String>>>> streamKnownTypes(TypeManager mgr)
    {
        Stream<Either<TaggedTypeDefinition, Pair<DataType, ImmutableList<String>>>> taggedTypes = mgr.getKnownTaggedTypes().values().stream().<Either<TaggedTypeDefinition, Pair<DataType, ImmutableList<String>>>>map(ttd -> Either.<TaggedTypeDefinition, Pair<DataType, ImmutableList<String>>>left(ttd));
        Stream<Pair<DataType, ImmutableList<String>>> basicTypes = Stream.<Pair<DataType, ImmutableList<String>>>of(
            new Pair<>(DataType.BOOLEAN, ImmutableList.<String>of("bool")),
            new Pair<>(DataType.NUMBER, ImmutableList.<String>of("int", "integer", "float", "double")),
            new Pair<>(DataType.TEXT, ImmutableList.<String>of("string"))
        );
        Stream<Pair<DataType, ImmutableList<String>>> dateTypes = Arrays.stream(DateTimeType.values()).<Pair<DataType, ImmutableList<String>>>map(dtt -> new Pair<>(DataType.date(new DateTimeInfo(dtt)), ImmutableList.<String>of()));
        return Stream.<Either<TaggedTypeDefinition, Pair<DataType, ImmutableList<String>>>>concat(taggedTypes, Stream.<Pair<DataType, ImmutableList<String>>>concat(basicTypes, dateTypes).<Either<TaggedTypeDefinition, Pair<DataType, ImmutableList<String>>>>map(t -> Either.<TaggedTypeDefinition, Pair<DataType, ImmutableList<String>>>right(t)));
    }

    @Override
    public void forNestedTagged(Consumer<TypeId> nestedTagged)
    {
        // No nested tagged types
    }

    @Override
    public <R, E extends Throwable> R apply(JellyTypeVisitorEx<R, E> visitor) throws InternalException, E
    {
        return visitor.ident(name);
    }

    @Override
    public void save(OutputBuilder output)
    {
        output.raw(name);
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JellyTypeIdent that = (JellyTypeIdent) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(name);
    }

    @Override
    public String toString()
    {
        return "JellyTypeIdent{" +
                "name='" + name + '\'' +
                '}';
    }
}
