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
import com.google.common.collect.ImmutableMap;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.id.TableAndColumnRenames;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataType.DateTimeInfo;
import xyz.columnal.data.datatype.DataType.DateTimeInfo.DateTimeType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.DataTypeUtility.StringView;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.expression.visitor.ExpressionVisitor;
import xyz.columnal.typeExp.TypeExp;
import xyz.columnal.styled.StyledString;
import xyz.columnal.utility.adt.Either;

import java.time.temporal.TemporalAccessor;
import java.util.Objects;

public class TemporalLiteral extends Literal
{
    private final String content;
    // The parsed version of content, if it parsed successfully, otherwise an error
    private final Either<StyledString, @Value TemporalAccessor> value;
    private final DateTimeType literalType;

    public TemporalLiteral(DateTimeType literalType, String content)
    {
        this.content = content.trim();
        this.literalType = literalType;
        StringView stringView = new StringView(content);
        Either<StyledString, @Value TemporalAccessor> v;
        try
        {
            v = Either.right(DataTypeUtility.parseTemporalFlexible(new DateTimeInfo(literalType), stringView));
            stringView.skipSpaces();
            if (stringView.getPosition() != content.length())
                v = Either.left(StyledString.s("Unrecognised content: " + stringView.snippet()));
        }
        catch (UserException e)
        {
            v = Either.left(e.getStyledMessage());
        }
        this.value = v;
    }

    @Override
    public String editString()
    {
        return content;
    }


    @Override
    protected @Nullable TypeExp checkType(TypeState typeState, LocationInfo locationInfo, ErrorAndTypeRecorder onError) throws InternalException
    {
        if (value.isLeft())
        {
            onError.recordError(this, StyledString.concat(styledExpressionInput(content), StyledString.s(" not recognised as "), DataType.date(new DateTimeInfo(literalType)).toStyledString()));
            return null;
        }
        
        return TypeExp.fromDataType(this, DataType.date(new DateTimeInfo(literalType)));
    }

    @Override
    public ValueResult calculateValue(EvaluateState state) throws InternalException
    {
        return result(value.<@Value Object>eitherInt(err -> {
            throw new InternalException("Executing with unrecognised date/time literal: " + content + " " + err);
        }, v -> v), state);
    }

    @Override
    public String save(SaveDestination saveDestination, BracketedStatus surround, TableAndColumnRenames renames)
    {
        String prefix;
        switch (literalType)
        {
            case YEARMONTHDAY:
                prefix = "date";
                break;
            case YEARMONTH:
                prefix = "dateym";
                break;
            case TIMEOFDAY:
                prefix = "time";
                break;
            case DATETIME:
                prefix = "datetime";
                break;
            case DATETIMEZONED:
                prefix = "datetimezoned";
                break;
            default:
                prefix = "date";
                break;
        }
        
        return prefix + "{" + content + "}";
    }

    @Override
    protected StyledString toDisplay(DisplayType displayType, BracketedStatus bracketedStatus, ExpressionStyler expressionStyler)
    {
        return expressionStyler.styleExpression(StyledString.s(save(SaveDestination.TO_STRING, bracketedStatus, new TableAndColumnRenames(ImmutableMap.of()))), this);
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TemporalLiteral that = (TemporalLiteral) o;
        return Objects.equals(content, that.content) &&
                Objects.equals(value, that.value) &&
                literalType == that.literalType;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(content, value, literalType);
    }

    @Override
    public <T> T visit(ExpressionVisitor<T> visitor)
    {
        return visitor.litTemporal(this, literalType, content, value);
    }
}
