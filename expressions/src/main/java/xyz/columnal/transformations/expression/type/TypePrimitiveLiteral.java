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
import xyz.columnal.log.Log;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.id.TableAndColumnRenames;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.jellytype.JellyType;
import xyz.columnal.loadsave.OutputBuilder;
import xyz.columnal.transformations.expression.Expression.SaveDestination;
import xyz.columnal.styled.StyledString;

import java.util.Objects;

// For a named, fully-formed type (not a tagged type name), not including numeric types
public class TypePrimitiveLiteral extends TypeExpression
{
    private final DataType dataType;

    public TypePrimitiveLiteral(DataType dataType)
    {
        this.dataType = dataType;
    }

    public String toDisplay()
    {
        try
        {
            return dataType.toDisplay(false);
        }
        catch (UserException | InternalException e)
        {
            Log.log(e);
            return "";
        }
    }

    @Override
    public StyledString toStyledString()
    {
        return dataType.toStyledString();
    }

    @Override
    public String save(SaveDestination saveDestination, TableAndColumnRenames renames)
    {
        try
        {
            return dataType.save(new OutputBuilder()).toString();
        }
        catch (InternalException e)
        {
            Log.log(e);
            return new InvalidIdentTypeExpression("").save(saveDestination, renames);
        }
    }

    @Override
    public @Nullable DataType toDataType(TypeManager typeManager)
    {
        return dataType;
    }

    @Override
    public @Recorded JellyType toJellyType(@Recorded TypePrimitiveLiteral this, TypeManager typeManager, JellyRecorder jellyRecorder) throws InternalException
    {
        return jellyRecorder.record(JellyType.fromConcrete(dataType), this);
    }

    @Override
    public boolean isEmpty()
    {
        return false;
    }

    public DataType _test_getType()
    {
        return dataType;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TypePrimitiveLiteral that = (TypePrimitiveLiteral) o;
        return Objects.equals(dataType, that.dataType);
    }

    @Override
    public int hashCode()
    {

        return Objects.hash(dataType);
    }

    @Override
    public TypeExpression replaceSubExpression(TypeExpression toReplace, TypeExpression replaceWith)
    {
        return this == toReplace ? replaceWith : this;
    }

    @SuppressWarnings("identifier")
    @Override
    public @Nullable @ExpressionIdentifier String asIdent()
    {
        return dataType.toString();
    }
}
