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
import xyz.columnal.grammar.FormatLexer;
import xyz.columnal.loadsave.OutputBuilder;
import xyz.columnal.typeExp.MutVar;
import xyz.columnal.typeExp.TypeCons;
import xyz.columnal.typeExp.TypeExp;
import xyz.columnal.typeExp.units.MutUnitVar;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.UnitType;
import xyz.columnal.utility.Utility;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static xyz.columnal.typeExp.TypeExp.ALL_TYPE_CLASSES;

public class JellyTypeApply extends JellyType
{
    private final TypeId typeName;
    private final ImmutableList<Either<JellyUnit, @Recorded JellyType>> typeParams;

    public JellyTypeApply(TypeId typeName, ImmutableList<Either<JellyUnit, @Recorded JellyType>> typeParams)
    {
        this.typeName = typeName;
        this.typeParams = typeParams;
    }

    @Override
    public TypeExp makeTypeExp(ImmutableMap<String, Either<MutUnitVar, MutVar>> typeVariables) throws InternalException
    {
        return new TypeCons(null, typeName.getRaw(), Utility.mapListInt(typeParams, p ->
            p.mapBothInt(u -> u.makeUnitExp(typeVariables), t -> t.makeTypeExp(typeVariables))
        ), ALL_TYPE_CLASSES);
    }

    @Override
    public DataType makeDataType(@Recorded JellyTypeApply this, ImmutableMap<String, Either<Unit, DataType>> typeVariables, TypeManager mgr) throws InternalException, UnknownTypeException, TaggedInstantiationException
    {
        ImmutableList.Builder<Either<Unit, DataType>> typeParamConcrete = ImmutableList.builderWithExpectedSize(typeParams.size());

        for (Either<JellyUnit, @Recorded JellyType> typeParam : typeParams)
        {
            typeParamConcrete.add(typeParam.<Unit, DataType, InternalException, UnknownTypeException, TaggedInstantiationException>mapBothEx3(u -> u.makeUnit(typeVariables), t -> t.makeDataType(typeVariables, mgr)));
        }
        
        DataType dataType = mgr.lookupType(typeName, typeParamConcrete.build());
        if (dataType != null)
            return dataType;
        ImmutableList<JellyType> fixes = Utility.findAlternatives(typeName.getRaw(), mgr.getKnownTaggedTypes().values().stream(), ttd -> Stream.<String>of(ttd.getTaggedTypeName().getRaw())).map(t -> new JellyTypeApply(t.getTaggedTypeName(), typeParams)).collect(ImmutableList.<JellyType>toImmutableList());
        throw new UnknownTypeException("Could not find data type: " + typeName, this, fixes);
    }

    @Override
    public void save(OutputBuilder output)
    {
        output.t(FormatLexer.APPLY, FormatLexer.VOCABULARY);
        output.raw(" ");
        output.unquoted(typeName);
        for (Either<JellyUnit, JellyType> typeParam : typeParams)
        {
            output.raw("(");
            typeParam.either(u -> {
                output.raw("{");
                u.save(output);
                output.raw("}");
                return UnitType.UNIT;
            },t -> {t.save(output); return UnitType.UNIT;});
            output.raw(")");
        }
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JellyTypeApply that = (JellyTypeApply) o;
        return Objects.equals(typeName, that.typeName) &&
            Objects.equals(typeParams, that.typeParams);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(typeName, typeParams);
    }

    @Override
    public void forNestedTagged(Consumer<TypeId> nestedTagged)
    {
        nestedTagged.accept(typeName);
        for (Either<JellyUnit, JellyType> typeParam : typeParams)
        {
            typeParam.ifRight(t -> t.forNestedTagged(nestedTagged));
        }
    }

    @Override
    public <R, E extends Throwable> R apply(JellyTypeVisitorEx<R, E> visitor) throws InternalException, E
    {
        return visitor.applyTagged(typeName, typeParams);
    }

    public TypeId getName()
    {
        return typeName;
    }
    
    public ImmutableList<Either<JellyUnit, @Recorded JellyType>> getTypeParams()
    {
        return typeParams;
    }

    @Override
    public String toString()
    {
        return "JellyTypeApply{" +
                "typeName=" + typeName +
                ", typeParams=" + Utility.listToString(typeParams) +
                '}';
    }
}
