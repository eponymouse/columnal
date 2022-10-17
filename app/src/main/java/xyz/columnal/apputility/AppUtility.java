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

package xyz.columnal.apputility;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.ImmediateValue;
import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataType.DataTypeVisitor;
import xyz.columnal.data.datatype.DataType.DateTimeInfo;
import xyz.columnal.data.datatype.DataType.TagType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.NumberInfo;
import xyz.columnal.data.datatype.TypeId;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.expression.ArrayExpression;
import xyz.columnal.transformations.expression.BooleanLiteral;
import xyz.columnal.transformations.expression.CallExpression;
import xyz.columnal.transformations.expression.Expression;
import xyz.columnal.transformations.expression.IdentExpression;
import xyz.columnal.transformations.expression.NumericLiteral;
import xyz.columnal.transformations.expression.RecordExpression;
import xyz.columnal.transformations.expression.StringLiteral;
import xyz.columnal.transformations.expression.TemporalLiteral;
import xyz.columnal.transformations.expression.TypeLiteralExpression;
import xyz.columnal.transformations.expression.UnitExpression;
import xyz.columnal.transformations.expression.function.FunctionLookup;
import xyz.columnal.transformations.expression.type.TypeExpression;
import xyz.columnal.transformations.function.core.AsType;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.TaggedValue;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Utility.ListEx;
import xyz.columnal.utility.Utility.Record;

import java.util.Map.Entry;

public class AppUtility
{
    @OnThread(Tag.FXPlatform)
    public static Expression valueToExpressionFX(TypeManager typeManager, FunctionLookup functionLookup, DataType dataType, @ImmediateValue Object value) throws UserException, InternalException
    {
        return Utility.launderSimulationEx(() -> valueToExpression(typeManager, functionLookup, dataType, value));
    }
    
    @OnThread(Tag.Simulation)
    @SuppressWarnings("recorded")
    public static Expression valueToExpression(TypeManager typeManager, FunctionLookup functionLookup, DataType dataType, @Value Object value) throws UserException, InternalException
    {
        return dataType.apply(new DataTypeVisitor<Expression>()
        {
            @Override
            public Expression number(NumberInfo numberInfo) throws InternalException, UserException
            {
                return new NumericLiteral(Utility.cast(value, Number.class), numberInfo.getUnit().equals(Unit.SCALAR) ? null : UnitExpression.load(numberInfo.getUnit()));
            }

            @Override
            public Expression text() throws InternalException, UserException
            {
                return new StringLiteral(Utility.cast(value, String.class));
            }

            @Override
            @OnThread(Tag.Simulation)
            public Expression date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
            {
                return new TemporalLiteral(dateTimeInfo.getType(), DataTypeUtility.valueToString(value));
            }

            @Override
            public Expression bool() throws InternalException, UserException
            {
                return new BooleanLiteral(Utility.cast(value, Boolean.class));
            }

            @Override
            @OnThread(Tag.Simulation)
            public Expression tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, UserException
            {
                @Value TaggedValue taggedValue = Utility.cast(value, TaggedValue.class);
                TagType<DataType> tag = tags.get(taggedValue.getTagIndex());
                IdentExpression constructor = IdentExpression.tag(typeName.getRaw(), tag.getName());
                @Value Object innerValue = taggedValue.getInner();
                DataType innerType = tag.getInner();
                Expression expression = innerValue == null || innerType == null ? constructor : new CallExpression(constructor, ImmutableList.of(valueToExpression(typeManager, functionLookup, innerType, innerValue)));
                if (typeVars.isEmpty())
                    return expression;
                else
                {
                    DataType lookedUp = typeManager.lookupType(typeName, typeVars);
                    if (lookedUp == null)
                        throw new UserException("Could not find type: " + dataType);
                    return asType(lookedUp, expression);
                }
            }

            @Override
            @OnThread(Tag.Simulation)
            public Expression record(ImmutableMap<@ExpressionIdentifier String, DataType> fields) throws InternalException, UserException
            {
                Record record = Utility.cast(value, Record.class);
                ImmutableList.Builder<Pair<@ExpressionIdentifier String, Expression>> members = ImmutableList.builderWithExpectedSize(fields.size());
                for (Entry<@ExpressionIdentifier String, DataType> field : fields.entrySet())
                {
                    members.add(new Pair<>(field.getKey(), valueToExpression(typeManager, functionLookup, field.getValue(), record.getField(field.getKey()))));
                }
                return new RecordExpression(members.build());
            }

            @Override
            @OnThread(Tag.Simulation)
            public Expression array(DataType inner) throws InternalException, UserException
            {
                ListEx listEx = Utility.cast(value, ListEx.class);
                int size = listEx.size();
                ImmutableList.Builder<Expression> members = ImmutableList.builderWithExpectedSize(size);

                for (int i = 0; i < size; i++)
                {
                    members.add(valueToExpression(typeManager, functionLookup, inner, listEx.get(i)));
                }
                
                if (size == 0)
                    return asType(inner, new ArrayExpression(members.build()));
                else
                    return new ArrayExpression(members.build());
            }

            private CallExpression asType(DataType inner, Expression expression) throws InternalException
            {
                return new CallExpression(functionLookup, AsType.NAME, new TypeLiteralExpression(TypeExpression.fromDataType(DataType.array(inner))), expression);
            }
        });
    }
}
