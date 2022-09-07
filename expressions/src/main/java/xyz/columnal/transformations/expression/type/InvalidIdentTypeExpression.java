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
import xyz.columnal.grammar.FormatLexer;
import xyz.columnal.jellytype.JellyType;
import xyz.columnal.loadsave.OutputBuilder;
import xyz.columnal.transformations.expression.Expression.SaveDestination;
import xyz.columnal.styled.StyledString;
import xyz.columnal.utility.IdentifierUtility;

import java.util.Objects;

public class InvalidIdentTypeExpression extends TypeExpression
{
    private final String value;
    
    public InvalidIdentTypeExpression(String value)
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
        if (saveDestination.needKeywords())
            return OutputBuilder.token(FormatLexer.VOCABULARY, FormatLexer.INCOMPLETE) + " " + OutputBuilder.quoted(value);
        else
            return value;
    }

    @Override
    public @Nullable DataType toDataType(TypeManager typeManager)
    {
        return null;
    }

    @Override
    public @Recorded JellyType toJellyType(@Recorded InvalidIdentTypeExpression this, TypeManager typeManager, JellyRecorder jellyRecorder) throws InternalException, UnJellyableTypeExpression
    {
        throw new UnJellyableTypeExpression("Invalid type expression: \"" + value + "\"", this);
    }

    @Override
    public boolean isEmpty()
    {
        return value.isEmpty();
    }

    public String _test_getContent()
    {
        return value;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InvalidIdentTypeExpression that = (InvalidIdentTypeExpression) o;
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

    // IdentExpression if possible, otherwise InvalidIdentExpression
    public static TypeExpression identOrUnfinished(String src)
    {
        @ExpressionIdentifier String valid = IdentifierUtility.asExpressionIdentifier(src);
        if (valid != null)
            return new IdentTypeExpression(valid);
        else
            return new InvalidIdentTypeExpression(src);
    }

    @Override
    public @Nullable @ExpressionIdentifier String asIdent()
    {
        return null;
    }
}
