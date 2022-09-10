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

package xyz.columnal.transformations.expression;

import annotation.qual.Value;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.id.TableAndColumnRenames;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.grammar.GrammarUtility;
import xyz.columnal.transformations.expression.visitor.ExpressionVisitor;
import xyz.columnal.typeExp.TypeExp;
import xyz.columnal.styled.CommonStyles;
import xyz.columnal.styled.StyledString;

/**
 * Created by neil on 25/11/2016.
 */
public class StringLiteral extends Literal
{
    // The actual String value, without any remaining escapes.
    private final @Value String value;
    private final String rawUnprocessed;

    public StringLiteral(String rawUnprocessed)
    {
        this.rawUnprocessed = rawUnprocessed;
        this.value = DataTypeUtility.value(GrammarUtility.processEscapes(rawUnprocessed, false));
    }

    @Override
    protected @Nullable TypeExp checkType(TypeState typeState, LocationInfo locationInfo, ErrorAndTypeRecorder onError)
    {
        return TypeExp.text(this);
    }

    @Override
    public ValueResult calculateValue(EvaluateState state)
    {
        return result(value, state);
    }

    @Override
    public String save(SaveDestination saveDestination, BracketedStatus surround, TableAndColumnRenames renames)
    {
        return "\"" + rawUnprocessed + "\"";
    }

    @Override
    protected StyledString toDisplay(DisplayType displayType, BracketedStatus bracketedStatus, ExpressionStyler expressionStyler)
    {
        return expressionStyler.styleExpression(StyledString.s("\"" + rawUnprocessed + "\"").withStyle(CommonStyles.MONOSPACE), this);
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        StringLiteral that = (StringLiteral) o;

        return rawUnprocessed.equals(that.rawUnprocessed);
    }

    @Override
    public int hashCode()
    {
        return rawUnprocessed.hashCode();
    }

    // Escapes the characters ready for editing.
    @Override
    public String editString()
    {
        return rawUnprocessed;
    }

    @Override
    public <T> T visit(ExpressionVisitor<T> visitor)
    {
        return visitor.litText(this, rawUnprocessed);
    }
}
