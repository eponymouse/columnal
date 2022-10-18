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

package test.gen.backwards;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import test.functions.TFunctionUtil;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataType.DataTypeVisitor;
import xyz.columnal.data.datatype.DataType.DateTimeInfo;
import xyz.columnal.data.datatype.DataType.TagType;
import xyz.columnal.data.datatype.NumberInfo;
import xyz.columnal.data.datatype.TaggedTypeDefinition;
import xyz.columnal.data.datatype.TypeId;
import xyz.columnal.data.datatype.TypeManager.TagInfo;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.expression.*;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.TaggedValue;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Utility.ListEx;
import xyz.columnal.utility.Utility.Record;

import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;

@SuppressWarnings("recorded")
public class BackwardsLiteral extends BackwardsProvider
{
    public BackwardsLiteral(SourceOfRandomness r, RequestBackwardsExpression parent)
    {
        super(r, parent);
    }

    @Override
    public List<ExpressionMaker> terminals(DataType targetType, @Value Object targetValue) throws InternalException, UserException
    {
        return targetType.apply(new DataTypeVisitor<List<ExpressionMaker>>()
        {
            @Override
            public List<ExpressionMaker> number(NumberInfo numberInfo) throws InternalException, UserException
            {
                return ImmutableList.of(
                    () -> new NumericLiteral((Number)targetValue, parent.makeUnitExpression(numberInfo.getUnit())),
                        () -> new TimesExpression(ImmutableList.of(new NumericLiteral((Number)targetValue, null), new NumericLiteral(1, parent.makeUnitExpression(numberInfo.getUnit()))))
                );
            }

            @Override
            public List<ExpressionMaker> text() throws InternalException, UserException
            {
                return ImmutableList.of(() -> TFunctionUtil.makeStringLiteral((String)targetValue, r));
            }

            @Override
            public List<ExpressionMaker> date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
            {
                return ImmutableList.of(
                    () -> {
                        return new TemporalLiteral(dateTimeInfo.getType(), dateTimeInfo.getStrictFormatter().format((TemporalAccessor)targetValue));
                    }
                );
            }

            @Override
            public List<ExpressionMaker> bool() throws InternalException, UserException
            {
                return ImmutableList.of(() -> new BooleanLiteral((Boolean)targetValue));
            }

            @Override
            public List<ExpressionMaker> tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, UserException
            {
                return ImmutableList.of(() -> {
                    @Value TaggedValue taggedValue = (TaggedValue) targetValue;
                    TagType<DataType> tag = tags.get(taggedValue.getTagIndex());

                    TaggedTypeDefinition typeDefinition = parent.getTypeManager().lookupDefinition(typeName);
                    if (typeDefinition == null)
                        throw new InternalException("Looked up type but null definition: " + typeName);
                    TagInfo tagInfo = typeDefinition._test_getTagInfos().get(taggedValue.getTagIndex());
                    DataType inner = tag.getInner();
                    @Value Object innerValue = taggedValue.getInner();
                    if (inner == null || innerValue == null)
                        return TFunctionUtil.tagged(parent.getTypeManager().getUnitManager(), tagInfo, null, targetType, true);
                    else
                        return TFunctionUtil.tagged(parent.getTypeManager().getUnitManager(), tagInfo, r.choose(terminals(inner, innerValue)).make(), targetType, true);
                });
            }

            @Override
            public List<ExpressionMaker> record(ImmutableMap<@ExpressionIdentifier String, DataType> fields) throws InternalException, InternalException
            {
                Record record = Utility.cast(targetValue, Record.class);
                return ImmutableList.of(() -> {
                    ArrayList<Pair<@ExpressionIdentifier String, Expression>> members = new ArrayList<>();
                    for (Entry<@ExpressionIdentifier String, DataType> entry : fields.entrySet())
                    {
                        List<ExpressionMaker> terminals = terminals(entry.getValue(), record.getField(entry.getKey()));
                        members.add(new Pair<>(entry.getKey(), terminals.get(r.nextInt(terminals.size())).make()));
                    }
                    Collections.shuffle(members, new Random(r.nextLong()));
                    return new RecordExpression(ImmutableList.copyOf(members));
                });
            }
            @Override
            public List<ExpressionMaker> array(DataType inner) throws InternalException, UserException
            {
                return ImmutableList.of(() -> {
                    ListEx list = (ListEx)targetValue; 
                    ImmutableList.Builder<Expression> exps = ImmutableList.builder();
                    for (int i = 0; i < list.size(); i++)
                    {
                        exps.add(r.choose(terminals(inner, list.get(i))).make());
                    }
                    return new ArrayExpression(exps.build());
                });
            }
        });
    }

    // Structural semi-literals: structural outer layer, then recurse for inner item.
    @Override
    public List<ExpressionMaker> deep(int maxLevels, DataType targetType, @Value Object targetValue) throws InternalException, UserException
    {
        return targetType.apply(new DataTypeVisitor<List<ExpressionMaker>>()
        {
            @Override
            public List<ExpressionMaker> number(NumberInfo numberInfo) throws InternalException, UserException
            {
                return ImmutableList.of();
            }

            @Override
            public List<ExpressionMaker> text() throws InternalException, UserException
            {
                return ImmutableList.of();
            }

            @Override
            public List<ExpressionMaker> date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
            {
                return ImmutableList.of();
            }

            @Override
            public List<ExpressionMaker> bool() throws InternalException, UserException
            {
                return ImmutableList.of();
            }

            @Override
            public List<ExpressionMaker> tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, UserException
            {
                return ImmutableList.of(() -> {
                    @Value TaggedValue taggedValue = (TaggedValue) targetValue;
                    TagType<DataType> tag = tags.get(taggedValue.getTagIndex());

                    TaggedTypeDefinition typeDefinition = parent.getTypeManager().lookupDefinition(typeName);
                    if (typeDefinition == null)
                        throw new InternalException("Looked up type but null definition: " + typeName);
                    TagInfo tagInfo = typeDefinition._test_getTagInfos().get(taggedValue.getTagIndex());
                    DataType inner = tag.getInner();
                    @Value Object innerValue = taggedValue.getInner();
                    if (inner == null || innerValue == null)
                        return TFunctionUtil.tagged(parent.getTypeManager().getUnitManager(), tagInfo, null, targetType, true);
                    else
                        return TFunctionUtil.tagged(parent.getTypeManager().getUnitManager(), tagInfo, parent.make(inner, innerValue, maxLevels - 1), targetType, true);
                });
            }

            @Override
            public List<ExpressionMaker> record(ImmutableMap<@ExpressionIdentifier String, DataType> fields) throws InternalException, InternalException
            {
                Record record = Utility.cast(targetValue, Record.class);
                return ImmutableList.of(() -> {
                    ArrayList<Pair<@ExpressionIdentifier String, Expression>> members = new ArrayList<>();
                    for (Entry<@ExpressionIdentifier String, DataType> entry : fields.entrySet())
                    {
                        members.add(new Pair<>(entry.getKey(), parent.make(entry.getValue(), record.getField(entry.getKey()), maxLevels - 1)));
                    }
                    Collections.shuffle(members, new Random(r.nextLong()));
                    return new RecordExpression(ImmutableList.copyOf(members));
                });
            }

            @Override
            public List<ExpressionMaker> array(DataType inner) throws InternalException, UserException
            {
                return ImmutableList.of(() -> {
                    ListEx list = (ListEx)targetValue;
                    ImmutableList.Builder<Expression> exps = ImmutableList.builder();
                    for (int i = 0; i < list.size(); i++)
                    {
                        exps.add(parent.make(inner, list.get(i), maxLevels - 1));
                    }
                    return new ArrayExpression(exps.build());
                });
            }
        });

    }
}
