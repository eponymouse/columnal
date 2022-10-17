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

package test.gen;

import annotation.identifier.qual.ExpressionIdentifier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.TBasicUtil;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.TaggedTypeDefinition;
import xyz.columnal.data.datatype.TaggedTypeDefinition.TypeVariableKind;
import xyz.columnal.data.datatype.TypeId;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.jellytype.JellyType;
import test.gen.type.GenJellyTypeMaker;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.Utility;

import java.util.ArrayList;

import static test.gen.type.GenJellyTypeMaker.TypeKinds.*;

public class GenTaggedTypeDefinition extends Generator<TaggedTypeDefinition>
{
    public GenTaggedTypeDefinition()
    {
        super(TaggedTypeDefinition.class);
    }
    
    @Override
    public TaggedTypeDefinition generate(SourceOfRandomness r, GenerationStatus status)
    {
        try
        {
            final ImmutableList<Pair<TypeVariableKind, @ExpressionIdentifier String>> typeVars;
            if (r.nextInt(3) == 1)
            {
                // Must use distinct to make sure no duplicate names:
                typeVars = TBasicUtil.makeList(r, 1, 4, () -> "" + r.nextChar('a', 'z')).stream().distinct().map(name -> new Pair<>(r.nextInt(3) == 1 ? TypeVariableKind.UNIT : TypeVariableKind.TYPE, name)).collect(ImmutableList.toImmutableList());
            }
            else
            {
                typeVars = ImmutableList.of();
            }
            GenJellyTypeMaker genDataType = new GenJellyTypeMaker(ImmutableSet.of(NUM_TEXT_TEMPORAL,
                    BOOLEAN, RECORD, LIST,
                    MAYBE_UNNESTED, OTHER_BUILTIN_TAGGED), typeVars.stream().map(p -> p.getSecond()).collect(ImmutableSet.toImmutableSet()), false);
            
            // Outside type variables are not visible in a new tagged type:
            boolean noInner = r.nextInt() % 3 == 1;
            ArrayList<@Nullable JellyType> types = noInner ? new ArrayList<@Nullable JellyType>() : new ArrayList<@Nullable JellyType>(TBasicUtil.makeList(r, 1, 10, () -> genDataType.generate(r, status).makeType()));
            int extraNulls = r.nextInt(5) + (types.isEmpty() ? 1 : 0);
            for (int i = 0; i < extraNulls; i++)
            {
                types.add(r.nextInt(types.size() + 1), null);
            }
            @SuppressWarnings("identifier")
            TypeId typeId = new TypeId("" + r.nextChar('A', 'Z') + r.nextChar('A', 'Z'));
            return new TaggedTypeDefinition(typeId, typeVars, Utility.mapListExI_Index(types, (i, t) -> {
                @SuppressWarnings("identifier")
                @ExpressionIdentifier String tagName = "T" + i;
                return new DataType.TagType<JellyType>(tagName, t);
            }));
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }
}
