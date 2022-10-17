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
import xyz.columnal.utility.function.simulation.SimulationFunction;
import xyz.columnal.utility.TaggedValue;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Utility.ListEx;
import xyz.columnal.transformations.expression.function.ValueFunction;
import xyz.columnal.utility.Utility.Record;

import java.time.temporal.TemporalAccessor;
import java.util.Map.Entry;

public class ToString extends FunctionDefinition
{
    public ToString() throws InternalException
    {
        super("conversion:to text");
    }

    @Override
    @OnThread(Tag.Simulation)
    public ValueFunction getInstance(TypeManager typeManager, SimulationFunction<String, Either<Unit, DataType>> paramTypes) throws InternalException, UserException
    {
        DataType type = paramTypes.apply("t").getRight("Variable t should be type but was unit");
        if (type == null)
            throw new InternalException("");
        return new Instance(type);
    }

    private static class Instance extends ValueFunction
    {
        private final DataType type;

        public Instance(DataType type)
        {
            this.type = type;
        }
        
        @Override
        public @Value Object _call() throws UserException, InternalException
        {
            return DataTypeUtility.value(convertToString(type, arg(0)));
        }

        @OnThread(Tag.Simulation)
        private static String convertToString(DataType type, @Value Object param) throws InternalException, UserException
        {
            return type.apply(new DataTypeVisitorEx<String, UserException>()
            {
                @Override
                @OnThread(Tag.Simulation)
                public String number(NumberInfo numberInfo) throws InternalException, InternalException
                {
                    return Utility.numberToString(Utility.cast(param, Number.class));
                }

                @Override
                @OnThread(Tag.Simulation)
                public String text() throws InternalException, InternalException
                {
                    return "\"" + GrammarUtility.escapeChars(Utility.cast(param, String.class)) + "\"";
                }

                @Override
                @OnThread(Tag.Simulation)
                public String date(DateTimeInfo dateTimeInfo) throws InternalException, InternalException
                {
                    return dateTimeInfo.getStrictFormatter().format(Utility.cast(param, TemporalAccessor.class));
                }

                @Override
                @OnThread(Tag.Simulation)
                public String bool() throws InternalException, InternalException
                {
                    return param.toString();
                }

                @Override
                @OnThread(Tag.Simulation)
                public String tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, UserException
                {
                    @Value TaggedValue taggedValue = Utility.cast(param, TaggedValue.class);
                    TagType<DataType> tag = tags.get(taggedValue.getTagIndex());
                    return tag.getName() + ((taggedValue.getInner() == null || tag.getInner() == null) ? "" : ("(" + convertToString(tag.getInner(), taggedValue.getInner()) + ")"));
                }

                @Override
                @OnThread(Tag.Simulation)
                public String record(ImmutableMap<@ExpressionIdentifier String, DataType> fields) throws InternalException, UserException
                {
                    @Value Record values = Utility.cast(param, Record.class);
                    StringBuilder s = new StringBuilder("(");
                    boolean first = true;
                    for (Entry<@ExpressionIdentifier String, DataType> entry : fields.entrySet())
                    {
                        if (!first)
                            s.append(", ");
                        first = false;
                        s.append(entry.getKey()).append(": ");
                        s.append(convertToString(entry.getValue(), values.getField(entry.getKey())));
                    }
                    return s.append(")").toString();
                }

                @Override
                @OnThread(Tag.Simulation)
                public String array(@Nullable DataType inner) throws InternalException, UserException
                {
                    if (inner == null)
                        return "[]";
                    
                    ListEx listEx = Utility.cast(param, ListEx.class);
                    StringBuilder s = new StringBuilder("[");
                    for (int i = 0; i < listEx.size(); i++)
                    {
                        if (i > 0)
                            s.append(", ");
                        s.append(convertToString(inner, listEx.get(i)));
                    }
                    return s.append("]").toString();
                }
            });
        }
    }
}
