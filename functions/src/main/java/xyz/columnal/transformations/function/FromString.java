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

package xyz.columnal.transformations.function;

import annotation.funcdoc.qual.FuncDocKey;
import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataType.DataTypeVisitorEx;
import xyz.columnal.data.datatype.DataType.DateTimeInfo;
import xyz.columnal.data.datatype.DataType.TagType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.DataTypeUtility.StringView;
import xyz.columnal.data.datatype.NumberInfo;
import xyz.columnal.data.datatype.TypeId;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.grammar.GrammarUtility;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.IdentifierUtility;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.function.simulation.SimulationFunction;
import xyz.columnal.utility.TaggedValue;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Utility.ListEx;
import xyz.columnal.utility.Utility.ListExList;
import xyz.columnal.transformations.expression.function.ValueFunction;
import xyz.columnal.utility.Utility.RecordMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

public class FromString
{

    public static final @FuncDocKey String FROM_TEXT_TO = "conversion:from text to";

    public static ImmutableList<FunctionDefinition> getFunctions() throws InternalException
    {
        return ImmutableList.of(
            new FunctionDefinition("conversion:from text")
            {
                @Override
                public @OnThread(Tag.Simulation) ValueFunction getInstance(TypeManager typeManager, SimulationFunction<String, Either<Unit, DataType>> paramTypes) throws InternalException, UserException
                {
                    DataType type = paramTypes.apply("t").getRight("Variable t should be type but was unit");
                    if (type == null)
                        throw new InternalException("Type t not found for from text");
                    return new ValueFunction()
                    {
                        @Override
                        public @OnThread(Tag.Simulation) @Value Object _call() throws InternalException, UserException
                        {
                            return convertEntireString(arg(0), type);
                        }
                    };
                }
            },
            new FunctionDefinition(FROM_TEXT_TO)
            {
                @Override
                public @OnThread(Tag.Simulation) ValueFunction getInstance(TypeManager typeManager, SimulationFunction<String, Either<Unit, DataType>> paramTypes) throws InternalException, UserException
                {
                    DataType type = paramTypes.apply("t").getRight("Variable t should be type but was unit");
                    if (type == null)
                        throw new InternalException("Type t not found for from text");
                    return new ValueFunction()
                    {
                        @Override
                        public @OnThread(Tag.Simulation) @Value Object _call() throws InternalException, UserException
                        {
                            return convertEntireString(arg(1), type);
                        }
                    };
                }
            }
        );
    }
    
    @OnThread(Tag.Simulation)
    public static @Value Object _test_fromString(String s, DataType type) throws UserException, InternalException
    {
        return convertEntireString(DataTypeUtility.value(s), type);
    }

    @OnThread(Tag.Simulation)
    @Value
    public static Object convertEntireString(@Value Object arg, DataType type) throws InternalException, UserException
    {
        @Value String src = Utility.cast(arg, String.class);
        StringView stringView = new StringView(src);
        stringView.skipSpaces();
        @Value Object value = convertFromString(type, stringView);
        stringView.skipSpaces();
        if (stringView.getPosition() < src.length())
            throw new UserException("Entire string was not used during conversion to " + type + ", remainder: " + stringView.snippet() + " from original \"" + src + "\"");
        return value;
    }

    // The StringView gets modified as we process it.
        @OnThread(Tag.Simulation)
        private static @Value Object convertFromString(DataType type, StringView src) throws InternalException, UserException
        {
            return type.apply(new DataTypeVisitorEx<@Value Object, UserException>()
            {
                @Override
                @OnThread(Tag.Simulation)
                public @Value Object number(NumberInfo numberInfo) throws InternalException, UserException
                {
                    // First, we need to consume the chars for the number:
                    StringBuilder s = new StringBuilder();
                    if (src.tryRead("+"))
                    {
                        s.append("+");
                    }
                    else if (src.tryRead("-"))
                    {
                        s.append("-");
                    }
                    s.append(src.consumeNumbers());
                    if (src.tryRead("."))
                    {
                        s.append(".").append(src.consumeNumbers());
                    }
                    
                    return Utility.parseNumber(s.toString());
                }

                @Override
                @OnThread(Tag.Simulation)
                public @Value Object text() throws InternalException, UserException
                {
                    if (!src.tryRead("\""))
                        throw new UserException("Expected start of string but found: " + src.snippet());
                    @Nullable String content = src.readUntil('\"');
                    if (content != null)
                        return DataTypeUtility.value(GrammarUtility.processEscapes("\"" + content + "\""));
                    else
                        throw new UserException("Could not find end of string");
                }

                @Override
                @OnThread(Tag.Simulation)
                public @Value Object date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
                {
                    return DataTypeUtility.parseTemporalFlexible(dateTimeInfo, src);
                }

                @Override
                @OnThread(Tag.Simulation)
                public @Value Object bool() throws InternalException, UserException
                {
                    if (src.tryReadIgnoreCase("true"))
                        return DataTypeUtility.value(true);
                    else if (src.tryReadIgnoreCase("false"))
                        return DataTypeUtility.value(false);
                    else
                        throw new UserException("Expected boolean but found: " + src.snippet());
                }

                @Override
                @OnThread(Tag.Simulation)
                public @Value Object tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, UserException
                {
                    ArrayList<Pair<Integer, TagType<DataType>>> indexedTags = new ArrayList<>();
                    for (int i = 0; i < tags.size(); i++)
                    {
                        indexedTags.add(new Pair<>(i, tags.get(i)));
                    }
                    // Need to check longest tags first, otherwise we may consume a short tag
                    // which is a prefix of a longer tag's name.  Compare by negative length
                    // to put longest first:
                    Collections.<Pair<Integer, TagType<DataType>>>sort(indexedTags, Comparator.<Pair<Integer, TagType<DataType>>, Integer>comparing(p -> -p.getSecond().getName().length()));

                    for (Pair<Integer, TagType<DataType>> indexedTag : indexedTags)
                    {
                        if (src.tryRead(indexedTag.getSecond().getName()))
                        {
                            // Found it!
                            final @Nullable DataType innerType = indexedTag.getSecond().getInner();
                            if (innerType != null)
                            {
                                if (!src.tryRead("("))
                                    throw new UserException("Tag name must be followed by round brackets around inner value");
                                @Value TaggedValue r = new TaggedValue(indexedTag.getFirst(), convertFromString(innerType, src), DataTypeUtility.fromTags(tags));
                                if (!src.tryRead(")"))
                                    throw new UserException("Missing closing round bracket around tag's inner value");
                                return r;
                            }
                            else
                            {
                                return new TaggedValue(indexedTag.getFirst(), null, DataTypeUtility.fromTags(tags));
                            }
                        }
                    }
                    throw new UserException("Looking for tags but found unrecognised tag: " + src.snippet());
                }

                @Override
                @OnThread(Tag.Simulation)
                public @Value Object record(ImmutableMap<@ExpressionIdentifier String, DataType> fields) throws InternalException, UserException
                {
                    if (!src.tryRead("("))
                        throw new UserException("Expected record, but no opening round bracket, instead found: " + src.snippet());


                    HashMap<@ExpressionIdentifier String, @Value Object> items = new HashMap<>();
                    for (int i = 0; i < fields.size(); i++)
                    {
                        if (i > 0)
                        {
                            // Must be comma if not first item:
                            if (!src.tryRead(","))
                                throw new UserException("Expected comma after record item but found: " + src.snippet());
                        }
                        String name = src.readUntil(':');
                        if (name == null)
                            throw new UserException("Expected field name followed by colon but found: " + src.snippet());
                        @ExpressionIdentifier String nameId = IdentifierUtility.asExpressionIdentifier(name.trim());
                        if (nameId == null)
                            throw new UserException("Invalid field name: " + name);
                        DataType type = fields.get(nameId);
                        if (type == null)
                            throw new UserException("Unknown field name: " + nameId);
                        items.put(nameId, convertFromString(type, src));
                    }
                    if (!src.tryRead(")"))
                        throw new UserException("Expected round bracket at end of record but found: " + src.snippet());
                    return DataTypeUtility.value(new RecordMap(items));
                }

                @Override
                @OnThread(Tag.Simulation)
                public @Value Object array(@Nullable DataType inner) throws InternalException, UserException
                {
                    if (!src.tryRead("["))
                        throw new UserException("Expected list, but no opening square bracket, instead found: " + src.snippet());
                    
                    if (src.tryRead("]"))
                        return ListEx.empty();
                    if (inner == null)
                        throw new UserException("Expected empty list but found data items: " + src.snippet());

                    ArrayList<@Value Object> items = new ArrayList<>();
                    while (!src.tryRead("]"))
                    {
                        if (!items.isEmpty())
                        {
                            // Must be comma if not first item:
                            if (!src.tryRead(","))
                                throw new UserException("Expected comma after list item but found " + src.snippet());
                        }
                        items.add(convertFromString(inner, src));
                    }
                    return new ListExList(items);
                }
            });
        }

}
