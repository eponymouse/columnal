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
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.TableAndColumnRenames;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.jellytype.JellyType;
import xyz.columnal.transformations.expression.Expression.SaveDestination;
import xyz.columnal.styled.StyledString;

import java.util.Objects;

public class ListTypeExpression extends TypeExpression
{
    private final @Recorded TypeExpression innerType;

    public ListTypeExpression(@Recorded TypeExpression innerType)
    {
        this.innerType = innerType;
    }

    @Override
    public StyledString toStyledString()
    {
        return StyledString.squareBracket(innerType.toStyledString());
    }

    @Override
    public String save(SaveDestination saveDestination, TableAndColumnRenames renames)
    {
        return "[" + innerType.save(saveDestination, renames) + "]";
    }

    @Override
    public @Nullable DataType toDataType(TypeManager typeManager)
    {
        // Be careful here; null is a valid value inside a list type, but we don't want to pass null!
        DataType inner = innerType.toDataType(typeManager);
        if (inner != null)
            return DataType.array(inner);
        return null;
    }

    @Override
    public @Recorded JellyType toJellyType(@Recorded ListTypeExpression this, TypeManager typeManager, JellyRecorder jellyRecorder) throws InternalException, UnJellyableTypeExpression
    {
        return jellyRecorder.record(JellyType.list(innerType.toJellyType(typeManager, jellyRecorder)), this);
    }

    @Override
    public boolean isEmpty()
    {
        return false;
    }

    public TypeExpression _test_getContent()
    {
        return innerType;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ListTypeExpression that = (ListTypeExpression) o;
        return Objects.equals(innerType, that.innerType);
    }

    @Override
    public int hashCode()
    {

        return Objects.hash(innerType);
    }

    @SuppressWarnings("recorded")
    @Override
    public TypeExpression replaceSubExpression(TypeExpression toReplace, TypeExpression replaceWith)
    {
        if (this == toReplace)
            return replaceWith;
        else
            return new ListTypeExpression(innerType.replaceSubExpression(toReplace, replaceWith));
    }

    @Override
    public @Nullable @ExpressionIdentifier String asIdent()
    {
        return null;
    }
}
