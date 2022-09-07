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

package xyz.columnal.transformations.expression.type;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import xyz.columnal.log.Log;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.TableAndColumnRenames;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.TaggedTypeDefinition;
import xyz.columnal.data.datatype.TypeId;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.jellytype.JellyType;
import xyz.columnal.transformations.expression.Expression.SaveDestination;
import xyz.columnal.styled.StyledString;

import java.util.Objects;

public class IdentTypeExpression extends TypeExpression
{
    private final @ExpressionIdentifier String value;

    public IdentTypeExpression(@ExpressionIdentifier String value)
    {
        this.value = value;
    }

    @Override
    public StyledString toStyledString()
    {
        return StyledString.s(value);
    }

    @Override
    public String save(SaveDestination saveDestination, TableAndColumnRenames renames)
    {
        return value;
    }

    @Override
    public @Nullable DataType toDataType(TypeManager typeManager)
    {
        // TODO pass in type variable subsitutions to this method
        try
        {
            TaggedTypeDefinition typeDefinition = typeManager.lookupDefinition(new TypeId(value));
            if (typeDefinition == null)
                return null;
            else
                return typeDefinition.instantiate(ImmutableList.of(), typeManager);
        }
        catch (InternalException | UserException e)
        {
            if (e instanceof InternalException)
                Log.log(e);
            return null;
        }
    }

    @Override
    public @Recorded JellyType toJellyType(@Recorded IdentTypeExpression this, TypeManager typeManager, JellyRecorder jellyRecorder)
    {
        return jellyRecorder.record(JellyType.typeVariable(value), this);
    }

    @Override
    public boolean isEmpty()
    {
        return value.isEmpty();
    }

    @Override
    public @ExpressionIdentifier String asIdent()
    {
        return value;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IdentTypeExpression that = (IdentTypeExpression) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(value);
    }

    @Override
    public TypeExpression replaceSubExpression(TypeExpression toReplace, TypeExpression replaceWith)
    {
        return this == toReplace ? replaceWith : this;
    }
}
