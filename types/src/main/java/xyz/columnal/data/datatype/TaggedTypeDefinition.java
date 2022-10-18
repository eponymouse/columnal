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

package xyz.columnal.data.datatype;

import annotation.identifier.qual.ExpressionIdentifier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.datatype.DataType.TagType;
import xyz.columnal.data.datatype.TypeManager.TagInfo;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.grammar.FormatLexer;
import xyz.columnal.jellytype.JellyType;
import xyz.columnal.jellytype.JellyType.UnknownTypeException;
import xyz.columnal.loadsave.OutputBuilder;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.TaggedValue.TaggedTypeDefinitionBase;
import xyz.columnal.utility.Utility;

import java.util.HashSet;
import java.util.Objects;

public class TaggedTypeDefinition implements TaggedTypeDefinitionBase
{
    public static enum TypeVariableKind { TYPE, UNIT }
    
    private final TypeId name;
    // Order matters here:
    private final ImmutableList<Pair<TypeVariableKind, @ExpressionIdentifier String>> typeVariables;
    // Version with the type variables unsubstituted:
    // The only free variables should be the ones in the typeVariables list:
    private final ImmutableList<TagType<JellyType>> tags;
    
    public TaggedTypeDefinition(TypeId name, ImmutableList<Pair<TypeVariableKind, @ExpressionIdentifier String>> typeVariables, ImmutableList<TagType<JellyType>> tags) throws InternalException
    {
        this.name = name;
        this.typeVariables = typeVariables;
        this.tags = tags;
        // Caller is responsible for checking name, type variables and tags.  Hence any violations here are InternalException, not UserException
        HashSet<String> uniqueTypeVars = new HashSet<>();
        for (Pair<TypeVariableKind, String> typeVariable : typeVariables)
        {
            if (!uniqueTypeVars.add(typeVariable.getSecond()))
            {
                throw new InternalException("Duplicate type variable: \"" + typeVariable + "\"");
            }
        }
        HashSet<String> uniqueTagNames = new HashSet<>();
        for (TagType<?> tag : tags)
        {
            if (!uniqueTagNames.add(tag.getName()))
            {
                throw new InternalException("Duplicate type tag: \"" + tag.getName() + "\"");
            }
        }
    }

    public ImmutableList<TagType<JellyType>> getTags()
    {
        return tags;
    }
    
    public static class TaggedInstantiationException extends UserException
    {
        public TaggedInstantiationException(String message)
        {
            super(message);
        }
    }

    // Instantiates to concrete type.
    public DataType instantiate(ImmutableList<Either<Unit, DataType>> typeVariableSubs, TypeManager mgr) throws TaggedInstantiationException, InternalException, UnknownTypeException
    {
        if (typeVariableSubs.size() != typeVariables.size())
            throw new TaggedInstantiationException("Attempting to use type with " + typeVariables.size() + " variables but trying to substitute " + typeVariableSubs.size());
        
        ImmutableMap.Builder<String, Either<Unit, DataType>> substitutionsBuilder = ImmutableMap.builder();

        for (int i = 0; i < typeVariables.size(); i++)
        {
            if ((typeVariables.get(i).getFirst() == TypeVariableKind.TYPE) && typeVariableSubs.get(i).isLeft())
                throw new TaggedInstantiationException("Expected type variable but found unit variable for variable #" + (i + 1));
            if ((typeVariables.get(i).getFirst() == TypeVariableKind.UNIT) && typeVariableSubs.get(i).isRight())
                throw new TaggedInstantiationException("Expected unit variable but found type variable for variable #" + (i + 1));
            
            substitutionsBuilder.put(typeVariables.get(i).getSecond(), typeVariableSubs.get(i));
        }
        ImmutableMap<String, Either<Unit, DataType>> substitutions = substitutionsBuilder.build();

        ImmutableList.Builder<TagType<DataType>> substitutedTags = ImmutableList.builderWithExpectedSize(tags.size());

        for (TagType<JellyType> tag : tags)
        {
            if (tag.getInner() == null)
                substitutedTags.add(new TagType<>(tag.getName(), null));
            else
            {
                @SuppressWarnings("recorded")
                @NonNull DataType inner = tag.getInner().makeDataType(substitutions, mgr);
                substitutedTags.add(new TagType<>(tag.getName(), inner));
            }
        }
        
        return DataType.tagged(name, typeVariableSubs, substitutedTags.build());
    }

    public TypeId getTaggedTypeName()
    {
        return name;
    }

    public void save(OutputBuilder b)
    {
        b.t(FormatLexer.TAGGED, FormatLexer.VOCABULARY);
        for (Pair<TypeVariableKind, @ExpressionIdentifier String> typeVariable : typeVariables)
        {
            b.t(typeVariable.getFirst() == TypeVariableKind.TYPE ? FormatLexer.TYPEVAR : FormatLexer.UNITVAR, FormatLexer.VOCABULARY)
                .expId(typeVariable.getSecond());
        }
        b.t(FormatLexer.OPEN_BRACKET, FormatLexer.VOCABULARY);
        boolean first = true;
        for (TagType<JellyType> tag : tags)
        {
            if (!first)
                b.t(FormatLexer.TAGOR, FormatLexer.VOCABULARY);
            b.expId(tag.getName());
            @Nullable JellyType inner = tag.getInner();
            if (inner != null)
            {
                b.raw("(");
                inner.save(b);
                b.raw(")");
            }
            first = false;
        }
        b.t(FormatLexer.CLOSE_BRACKET, FormatLexer.VOCABULARY);
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TaggedTypeDefinition that = (TaggedTypeDefinition) o;
        return Objects.equals(name, that.name) &&
            Objects.equals(typeVariables, that.typeVariables) &&
            Objects.equals(tags, that.tags);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(name, typeVariables, tags);
    }

    public ImmutableList<Pair<TypeVariableKind, @ExpressionIdentifier String>> getTypeArguments()
    {
        return typeVariables;
    }

    @Override
    public String toString()
    {
        return name + " " + Utility.listToString(typeVariables) + " " + Utility.listToString(tags);
    }

    public ImmutableList<TagInfo> _test_getTagInfos()
    {
        try
        {
            ImmutableList.Builder<TagInfo> r = ImmutableList.builder();
            for (int i = 0; i < tags.size(); i++)
            {
                r.add(new TagInfo(this, i));
            }
            return r.build();
        }
        catch (InternalException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getTagName(int tagIndex)
    {
        if (tagIndex >=0 && tagIndex < tags.size())
            return tags.get(tagIndex).getName();
        return "InvalidTag" + (tagIndex < 0 ? "Neg" : "") + Math.abs(tagIndex);
    }
}
