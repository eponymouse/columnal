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

package xyz.columnal.loadsave;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.antlr.v4.runtime.Vocabulary;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataType.DateTimeInfo;
import xyz.columnal.data.datatype.NumberInfo;
import xyz.columnal.data.datatype.DataType.TagType;
import xyz.columnal.data.datatype.DataTypeValue;
import xyz.columnal.data.datatype.DataTypeValue.DataTypeVisitorGet;
import xyz.columnal.data.datatype.DataTypeValue.GetValue;
import xyz.columnal.data.datatype.TypeId;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.InvalidImmediateValueException;
import xyz.columnal.error.UserException;
import xyz.columnal.grammar.GrammarUtility;
import xyz.columnal.grammar.MainLexer;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.id.ColumnId;
import xyz.columnal.id.SaveTag;
import xyz.columnal.id.TableId;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.ExBiFunction;
import xyz.columnal.utility.TaggedValue;
import xyz.columnal.utility.UnitType;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Utility.ListEx;
import xyz.columnal.utility.Utility.Record;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Created by neil on 09/11/2016.
 */
@OnThread(Tag.FXPlatform)
public class OutputBuilder
{
    // Each outer item is a line; each inner item is a list of tokens to be glued together with whitespace
    @OnThread(value = Tag.Any, requireSynchronized = true)
    private final ArrayList<List<String>> lines = new ArrayList<>();
    @OnThread(value = Tag.Any, requireSynchronized = true)
    private @Nullable ArrayList<String> curLine = null;
    @OnThread(value = Tag.Any, requireSynchronized = true)
    private final ArrayList<String> curLinePrefixStack = new ArrayList<>();

    @OnThread(Tag.Any)
    public OutputBuilder()
    {

    }

    // Gets the current line, making a new one if needed
    @OnThread(Tag.Any)
    private synchronized ArrayList<String> cur()
    {
        if (curLine == null)
            curLine = new ArrayList<>(curLinePrefixStack);
        return curLine;
    }

    // Outputs a token from MainLexer
    @OnThread(Tag.Any)
    public synchronized OutputBuilder t(int token)
    {
        return t(token, MainLexer.VOCABULARY);
    }

    // Outputs a token from given vocab
    @OnThread(Tag.Any)
    public synchronized OutputBuilder t(int token, Vocabulary vocabulary)
    {
        String literalName = vocabulary.getLiteralName(token);
        // Awkward to throw an exception here.  Tests should pick this up.
        //if (literalName == null)
        //    throw new InternalException("Unknown token in vocabulary: " + token);
        cur().add(stripQuotes(literalName));
        return this;
    }
    
    @OnThread(Tag.Any)
    public static String token(Vocabulary vocabulary, int token)
    {
        String literalName = vocabulary.getLiteralName(token);
        // Awkward to throw an exception here.  Tests should pick this up.
        //if (literalName == null)
        //    throw new InternalException("Unknown token in vocabulary: " + token);
        return stripQuotes(literalName);
    }

    @OnThread(Tag.Any)
    public static String stripQuotes(String quoted)
    {
        if (quoted.startsWith("'") && quoted.endsWith("'"))
            return quoted.substring(1, quoted.length() - 1);
        else
            throw new IllegalArgumentException("Could not remove quotes: <<" + quoted + ">>");
    }

    // Outputs a table identifier, quoted if necessary
    @OnThread(Tag.Any)
    public synchronized OutputBuilder id(TableId id)
    {
        return id(id.getOutput(), QuoteBehaviour.QUOTE_SPACES);
    }

    // Outputs a column identifier, quoted if necessary
    @OnThread(Tag.Any)
    public synchronized OutputBuilder id(ColumnId id)
    {
        return id(id.getOutput(), QuoteBehaviour.QUOTE_SPACES);
    }

    // Outputs an expression identifier, with no quotes
    @OnThread(Tag.Any)
    public synchronized OutputBuilder expId(@ExpressionIdentifier String id)
    {
        return raw(id);
    }

    @OnThread(Tag.Any)
    public synchronized OutputBuilder quote(TypeId id)
    {
        cur().add(quoted(id.getOutput()));
        return this;
    }

    // Outputs a column identifier, quoted if necessary
    @OnThread(Tag.Any)
    public synchronized OutputBuilder quote(ColumnId id)
    {
        cur().add(quoted(id.getOutput()));
        return this;
    }

    @OnThread(Tag.Any)
    public synchronized OutputBuilder unquoted(TypeId taggedTypeName)
    {
        cur().add(taggedTypeName.getRaw());
        return this;
    }

    @OnThread(Tag.Any)
    public synchronized OutputBuilder unquoted(ColumnId columnName)
    {
        cur().add(columnName.getRaw());
        return this;
    }

    // Blank the current line.  Used in exception catch blocks
    // to not leave a partially-completed line.
    @OnThread(Tag.Any)
    public synchronized void undoCurLine()
    {
        curLine = null;
    }

    public static enum QuoteBehaviour
    {
        ALWAYS_QUOTE, QUOTE_SPACES, DEFAULT;
        
        public String process(String original)
        {
            switch (this)
            {
                case ALWAYS_QUOTE:
                    return quoted(original);
                case QUOTE_SPACES:
                    if (original.contains(" "))
                        return quoted(original);
                    else
                        return quotedIfNecessary(original);
                default:
                    return quotedIfNecessary(original);
            }
        }
    }

    // Outputs an identifier, quoted if necessary
    @OnThread(Tag.Any)
    public synchronized OutputBuilder id(String id, QuoteBehaviour quoteBehaviour)
    {
        cur().add(quoteBehaviour.process(id));
        return this;
    }

    // Outputs a quoted absolute path
    @OnThread(Tag.Any)
    public synchronized OutputBuilder path(Path path)
    {
        cur().add(quoted(path.toFile().getAbsolutePath()));
        return this;
    }

    // Add a newline
    @OnThread(Tag.Any)
    public synchronized OutputBuilder nl()
    {
        lines.add(cur());
        curLine = null;
        return this;
    }


    @OnThread(Tag.Any)
    public static String quoted(String s)
    {
        // Order matters; escape ^ by itself first:
        return "\"" + GrammarUtility.escapeChars(s) + "\"";
    }

    @OnThread(Tag.Any)
    private static String quotedIfNecessary(String s)
    {
        if (GrammarUtility.validUnquoted(s))
            return s;
        else
            return quoted(s);

    }

    @OnThread(Tag.Any)
    public synchronized List<String> toLines()
    {
        ArrayList<String> finished = new ArrayList<>(Utility.<List<String>, String>mapList(lines, line -> line.stream().collect(Collectors.joining(" "))));
        if (curLine != null)
            finished.add(curLine.stream().collect(Collectors.joining(" ")));
        return finished;
    }

    @Override
    @OnThread(Tag.Any)
    public synchronized String toString()
    {
        String finished = lines.stream().map(line -> line.stream().collect(Collectors.joining(" ")) + "\n").collect(Collectors.joining());
        if (curLine != null)
            finished += curLine.stream().collect(Collectors.joining(" "));
        return finished;
    }

    // Outputs a single value
    @OnThread(Tag.Any)
    public synchronized OutputBuilder dataValue(DataType type, @Value Object value) throws UserException, InternalException
    {
        // Defeat thread checker:
        return ((ExBiFunction<DataTypeValue, Integer, OutputBuilder>)this::data).apply(type.fromCollapsed((i, prog) -> value), 0);
    }

    // Outputs an element of an entire data set
    @OnThread(Tag.Simulation)
    public synchronized OutputBuilder data(DataTypeValue type, int index) throws UserException, InternalException
    {
        return data(type, index, false, false);
    }
    
    @OnThread(Tag.Simulation)
    private synchronized OutputBuilder data(DataTypeValue type, int index, boolean nested, boolean alreadyRoundBracketed) throws UserException, InternalException
    {
        int curLength = cur().size();
        try
        {
            type.applyGet(new DataTypeVisitorGet<UnitType>()
            {
                @Override
                @OnThread(Tag.Simulation)
                public UnitType number(GetValue<@Value Number> g, NumberInfo displayInfo) throws InternalException, UserException
                {
                    @Value @NonNull Number number = g.get(index);
                    if (number instanceof BigDecimal)
                        raw(((BigDecimal) number).toPlainString());
                    else
                        raw(number.toString());
                    return UnitType.UNIT;
                }

                @Override
                @OnThread(Tag.Simulation)
                public UnitType text(GetValue<@Value String> g) throws InternalException, UserException
                {
                    raw(quoted(g.get(index)));
                    return UnitType.UNIT;
                }

                @Override
                @OnThread(Tag.Simulation)
                public UnitType tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tagTypes, GetValue<@Value TaggedValue> g) throws InternalException, UserException
                {
                    @Value TaggedValue v = g.get(index);
                    TagType<DataType> t = tagTypes.get(v.getTagIndex());
                    @Nullable DataType innerType = t.getInner();
                    raw(t.getName());
                    if (innerType != null)
                    {
                        raw("(");
                        data(innerType.fromCollapsed((i, prog) -> {
                            @Value Object innerValue = v.getInner();
                            if (innerValue == null)
                                throw new InternalException("Missing inner value required by type: " + typeName + " tag " + v.getTagIndex());
                            return innerValue;
                        }), 0, true, true);
                        raw(")");
                    }
                    return UnitType.UNIT;
                }

                @Override
                @OnThread(Tag.Simulation)
                public UnitType bool(GetValue<@Value Boolean> g) throws InternalException, UserException
                {
                    raw(g.get(index).toString());
                    return UnitType.UNIT;
                }

                @Override
                @OnThread(Tag.Simulation)
                public UnitType date(DateTimeInfo dateTimeInfo, GetValue<@Value TemporalAccessor> g) throws InternalException, UserException
                {
                    raw(dateTimeInfo.getStrictFormatter().format(g.get(index)));
                    return UnitType.UNIT;
                }

                @Override
                @OnThread(Tag.Simulation)
                public UnitType record(ImmutableMap<@ExpressionIdentifier String, DataType> types, GetValue<@Value Record> g) throws InternalException, UserException
                {
                   if (!alreadyRoundBracketed)
                        raw("(");
                    boolean first = true;
                    Record record = g.get(index);
                    for (Entry<@ExpressionIdentifier String, DataType> entry : Utility.iterableStream(types.entrySet().stream().sorted(Comparator.comparing(e -> e.getKey()))))
                    {
                        if (!first)
                            raw(",");
                        first = false;
                        id(entry.getKey(), QuoteBehaviour.ALWAYS_QUOTE).raw(":");
                        dataValue(entry.getValue(), record.getField(entry.getKey()));
                    }
                    if (!alreadyRoundBracketed)
                        raw(")");
                    return UnitType.UNIT;
                }

                @Override
                @OnThread(Tag.Simulation)
                public UnitType array(DataType inner, GetValue<@Value ListEx> g) throws InternalException, UserException
                {
                    raw("[");
                    @NonNull ListEx details = g.get(index);
                    for (int i = 0; i < details.size(); i++)
                    {
                        if (i > 0)
                            raw(",");
                        data(inner.fromCollapsed((j, prog) -> details.get(j)), i, true, false);
                    }
                    raw("]");
                    return UnitType.UNIT;
                }
            });
        }
        catch (InvalidImmediateValueException e)
        {
            if (nested)
                throw e;
            // Remove any added output:
            cur().subList(curLength, cur().size()).clear();
            raw("@INVALID \"" + GrammarUtility.escapeChars(e.getInvalid()) + "\"");
        }
        return this;
    }

    // Don't forget, this will get an extra space added to it as spacing
    @OnThread(Tag.Any)
    public synchronized OutputBuilder ws(String whiteSpace)
    {
        cur().add(whiteSpace);
        return this;
    }

    // Outputs the set of lines between @BEGIN/@END tags
    @OnThread(Tag.Simulation)
    public synchronized OutputBuilder inner(Supplier<List<String>> genDetail, SaveTag saveTag)
    {
        begin().ws(" ").raw(saveTag.getTag()).nl();
        pushPrefix(saveTag);
        pushIndent();
        for (String line : genDetail.get())
        {
            raw(line).nl();
        }
        end().nl();
        pop();
        pop();
        return this;
    }

    @OnThread(Tag.Any)
    public synchronized void pop()
    {
        curLinePrefixStack.remove(curLinePrefixStack.size() - 1);
    }

    @OnThread(Tag.Any)
    public synchronized void pushIndent()
    {
        curLinePrefixStack.add("");
    }

    @OnThread(Tag.Any)
    public synchronized void pushPrefix(SaveTag saveTag)
    {
        curLinePrefixStack.add(saveTag.getTag());
    }

    @OnThread(Tag.Any)
    public OutputBuilder begin()
    {
        return raw("@BEGIN");
    }

    // Outputs the given raw string
    @OnThread(Tag.Any)
    public OutputBuilder raw(String item)
    {
        cur().add(item);
        return this;
    }

    // Adds spacing at the current position
    @OnThread(Tag.Any)
    public OutputBuilder indent()
    {
        // Second space will be added after:
        return ws(" ");
    }

    @OnThread(Tag.Any)
    public OutputBuilder end()
    {
        return raw("@END");
    }

    // Outputs a number (without E-notation)
    @OnThread(Tag.Any)
    public synchronized OutputBuilder d(double number)
    {
        cur().add(String.format("%f", number));
        return this;
    }

    // Outputs an arbitrary keyword
    @OnThread(Tag.Any)
    public synchronized OutputBuilder kw(String keyword)
    {
        return raw(keyword);
    }

    @OnThread(Tag.Any)
    public synchronized void s(String string)
    {
        cur().add(quoted(string));
    }

    @OnThread(Tag.Any)
    public synchronized OutputBuilder unit(String s)
    {
        cur().add("{" + s + "}");
        return this;
    }

    @OnThread(Tag.Any)
    public synchronized OutputBuilder n(long n)
    {
        cur().add(Long.toString(n));
        return this;
    }
}
