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

package xyz.columnal.data.datatype;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.ImmediateValue;
import annotation.qual.UnknownIfValue;
import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import xyz.columnal.grammar.DataParser.BoolContext;
import xyz.columnal.grammar.DataParser.BoolOrInvalidContext;
import xyz.columnal.grammar.DataParser.InvalidItemContext;
import xyz.columnal.grammar.DataParser.LabelContext;
import xyz.columnal.grammar.DataParser.LocalDateTimeOrInvalidContext;
import xyz.columnal.grammar.DataParser.LocalTimeOrInvalidContext;
import xyz.columnal.grammar.DataParser.NumberContext;
import xyz.columnal.grammar.DataParser.NumberOrInvalidContext;
import xyz.columnal.grammar.DataParser.OpenRoundOrInvalidContext;
import xyz.columnal.grammar.DataParser.OpenSquareContext;
import xyz.columnal.grammar.DataParser.OpenSquareOrInvalidContext;
import xyz.columnal.grammar.DataParser.StringContext;
import xyz.columnal.grammar.DataParser.StringOrInvalidContext;
import xyz.columnal.grammar.DataParser.TagContext;
import xyz.columnal.grammar.DataParser.TagOrInvalidContext;
import xyz.columnal.grammar.DataParser.YmOrInvalidContext;
import xyz.columnal.grammar.DataParser.YmdOrInvalidContext;
import xyz.columnal.grammar.DataParser.ZonedDateTimeOrInvalidContext;
import xyz.columnal.log.Log;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import xyz.columnal.data.datatype.DataTypeValue.GetValue;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.parse.ParseException;
import xyz.columnal.error.UserException;
import xyz.columnal.grammar.DataLexer2;
import xyz.columnal.grammar.DataParser;
import xyz.columnal.grammar.DataParser2;
import xyz.columnal.grammar.FormatLexer;
import xyz.columnal.loadsave.OutputBuilder;
import xyz.columnal.styled.StyledShowable;
import xyz.columnal.styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.FunctionInt;
import xyz.columnal.utility.IdentifierUtility;
import xyz.columnal.utility.TaggedValue;
import xyz.columnal.utility.UnitType;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Utility.ListEx;
import xyz.columnal.utility.Utility.ListExList;
import xyz.columnal.utility.Utility.Record;
import xyz.columnal.utility.Utility.RecordMap;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.SignStyle;
import java.time.format.TextStyle;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.time.temporal.ChronoField.DAY_OF_MONTH;
import static java.time.temporal.ChronoField.HOUR_OF_DAY;
import static java.time.temporal.ChronoField.MINUTE_OF_HOUR;
import static java.time.temporal.ChronoField.MONTH_OF_YEAR;
import static java.time.temporal.ChronoField.NANO_OF_SECOND;
import static java.time.temporal.ChronoField.SECOND_OF_MINUTE;
import static java.time.temporal.ChronoField.YEAR;
import static xyz.columnal.data.datatype.DataType.DateTimeInfo.F.AMPM;
import static xyz.columnal.data.datatype.DataType.DateTimeInfo.F.DAY;
import static xyz.columnal.data.datatype.DataType.DateTimeInfo.F.HOUR;
import static xyz.columnal.data.datatype.DataType.DateTimeInfo.F.HOUR12;
import static xyz.columnal.data.datatype.DataType.DateTimeInfo.F.MIN;
import static xyz.columnal.data.datatype.DataType.DateTimeInfo.F.MONTH_NUM;
import static xyz.columnal.data.datatype.DataType.DateTimeInfo.F.MONTH_TEXT_LONG;
import static xyz.columnal.data.datatype.DataType.DateTimeInfo.F.MONTH_TEXT_SHORT;
import static xyz.columnal.data.datatype.DataType.DateTimeInfo.F.SEC_OPT_FRAC_OPT;
import static xyz.columnal.data.datatype.DataType.DateTimeInfo.F.YEAR2;
import static xyz.columnal.data.datatype.DataType.DateTimeInfo.F.YEAR4;

/**
 * A data type can be the following:
 *
 *  - A built-in/primitive type:
 *    - A number.  This has a small bit of dynamic typing: it may be
 *      integers or decimals, but this is a performance optimisation
 *      not a user-visible difference.
 *    - A string.
 *    - A date.
 *    - A boolean
 *  - A composite type:
 *    - A set of 2+ tags.  Each tag may have 0 or 1 arguments (think Haskell's
 *      ADTs, but where you either have a tuple as an arg or nothing).
 *    - A record (named fields) of 2+ types.
 *    - An array (i.e. variable-length list) of items of a single type.
 *
 *  Written in pseudo-Haskell:
 *  data Type = N Number | T String | D Date | B Boolean
 *            | Tags [(TagName, Maybe Type)]
 *            | Record [(FieldName, Type)] 
 *            | Array Type
 */
public abstract class DataType implements StyledShowable
{

    public final DataTypeValue fromCollapsed(GetValue<@Value Object> get) throws InternalException
    {
        return apply(new DataTypeVisitorEx<DataTypeValue, InternalException>()
        {
            @SuppressWarnings("valuetype")
            private <T extends @NonNull Object> GetValue<@Value T> castTo(Class<T> cls)
            {
                return new GetValue<T>()
                {
                    @Override
                    public @NonNull @Value T getWithProgress(int i,  @Nullable ProgressListener prog) throws UserException, InternalException
                    {
                        Object value = get.getWithProgress(i, prog);
                        if (!cls.isAssignableFrom(value.getClass()))
                            throw new InternalException("Type inconsistency: should be " + cls + " but is " + value.getClass());
                        return cls.cast(value);
                    }

                    @SuppressWarnings("nullness") // Not sure why this is needed
                    @Override
                    public void set(int index, Either<String, @Value T> value) throws InternalException, UserException
                    {
                        get.set(index, value.<@NonNull @Value Object>map(v -> v));
                    }
                };
            }

            @Override
            @OnThread(Tag.Simulation)
            public DataTypeValue number(NumberInfo displayInfo) throws InternalException
            {
                return DataTypeValue.number(displayInfo, castTo(Number.class));
            }

            @Override
            @OnThread(Tag.Simulation)
            public DataTypeValue text() throws InternalException
            {
                return DataTypeValue.text(castTo(String.class));
            }

            @Override
            @OnThread(Tag.Simulation)
            public DataTypeValue date(DateTimeInfo dateTimeInfo) throws InternalException
            {
                return DataTypeValue.date(dateTimeInfo, castTo(TemporalAccessor.class));
            }

            @Override
            @OnThread(Tag.Simulation)
            public DataTypeValue bool() throws InternalException
            {
                return DataTypeValue.bool(castTo(Boolean.class));
            }

            @Override
            @OnThread(Tag.Simulation)
            public DataTypeValue tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException
            {
                GetValue<TaggedValue> getTaggedValue = castTo(TaggedValue.class);
                return DataTypeValue.tagged(typeName, typeVars, tags, getTaggedValue);
            }

            @Override
            @OnThread(Tag.Simulation)
            public DataTypeValue record(ImmutableMap<@ExpressionIdentifier String, DataType> fields) throws InternalException, InternalException
            {
                return DataTypeValue.record(fields, castTo(Record.class));
            }

            @Override
            @OnThread(Tag.Simulation)
            public DataTypeValue array(DataType inner) throws InternalException
            {
                GetValue<@Value ListEx> getList = castTo(ListEx.class);
                return DataTypeValue.array(inner, getList);
            }
        });
    }
    
    public static DataType function(ImmutableList<DataType> argType, DataType resultType)
    {
        return new FunctionDataType(argType, resultType);
    }
    
    /*
    final Kind kind;
    // For NUMBER:
    final @Nullable NumberInfo numberInfo;
    // for DATETIME:
    final @Nullable DateTimeInfo dateTimeInfo;
    // For TAGGED:
    final @Nullable TypeId taggedTypeName;
    // We store the declared type variables here even through they should already be substituted,
    // in case we need to turn this concrete type back into a TypeExp for unification:
    final @Nullable ImmutableList<Either<Unit, DataType>> tagTypeVariableSubstitutions;
    final @Nullable ImmutableList<TagType<DataType>> tagTypes;
    // For TUPLE (2+) and ARRAY (1) and FUNCTION(2).  If ARRAY and memberType is empty, indicates
    // the empty array (which can type-check against any array type)
    final @Nullable ImmutableList<DataType> memberType;
*/

    // package-visible
    DataType()
    {
    }

    public static final DataType NUMBER = DataType.number(NumberInfo.DEFAULT);
    public static final DataType BOOLEAN = new BooleanDataType();
    public static final DataType TEXT = new TextDataType();

    public static DataType array(DataType inner)
    {
        return new ListDataType(inner);
    }

    public static DataType record(Map<@ExpressionIdentifier String, DataType> fields)
    {
        return new RecordDataType(fields);
    }

    public static DataType date(DateTimeInfo dateTimeInfo)
    {
        return new TemporalDataType(dateTimeInfo);
    }

    public static interface DataTypeVisitorEx<R, E extends Throwable>
    {
        R number(NumberInfo numberInfo) throws InternalException, E;
        R text() throws InternalException, E;
        R date(DateTimeInfo dateTimeInfo) throws InternalException, E;
        R bool() throws InternalException, E;

        R tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, E;
        R record(ImmutableMap<@ExpressionIdentifier String, DataType> fields) throws InternalException, E;
        R array(DataType inner) throws InternalException, E;
        
        default R function(ImmutableList<DataType> argTypes, DataType resultType) throws InternalException, E
        {
            throw new InternalException("Functions are unsupported, plain data values expected");
        };
    }

    public static interface DataTypeVisitor<R> extends DataTypeVisitorEx<R, UserException>
    {
    }

    public static class SpecificDataTypeVisitor<R> implements DataTypeVisitorEx<R, InternalException>
    {
        @Override
        public R number(NumberInfo displayInfo) throws InternalException
        {
            throw new InternalException("Unexpected number data type");
        }

        @Override
        public R text() throws InternalException
        {
            throw new InternalException("Unexpected text data type");
        }

        @Override
        public R tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException
        {
            throw new InternalException("Unexpected tagged data type");
        }

        @Override
        public R bool() throws InternalException
        {
            throw new InternalException("Unexpected boolean type");
        }

        @Override
        public R date(DateTimeInfo dateTimeInfo) throws InternalException
        {
            throw new InternalException("Unexpected date type");
        }

        @Override
        public R record(ImmutableMap<@ExpressionIdentifier String, DataType> fields) throws InternalException, InternalException
        {
            throw new InternalException("Unexpected record type");
        }

        @Override
        public R array(DataType inner) throws InternalException
        {
            throw new InternalException("Unexpected array type");
        }
    }
    
    public static class FlatDataTypeVisitor<T> implements DataTypeVisitorEx<T, InternalException>
    {
        private final T def;

        public FlatDataTypeVisitor(T def)
        {
            this.def = def;
        }

        @Override
        public T number(NumberInfo numberInfo) throws InternalException, InternalException
        {
            return def;
        }

        @Override
        public T text() throws InternalException, InternalException
        {
            return def;
        }

        @Override
        public T date(DateTimeInfo dateTimeInfo) throws InternalException, InternalException
        {
            return def;
        }

        @Override
        public T bool() throws InternalException, InternalException
        {
            return def;
        }

        @Override
        public T tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, InternalException
        {
            return def;
        }

        @Override
        public T record(ImmutableMap<@ExpressionIdentifier String, DataType> fields) throws InternalException, InternalException
        {
            return def;
        }

        @Override
        public T array(DataType inner) throws InternalException, InternalException
        {
            return def;
        }

        @Override
        public T function(ImmutableList<DataType> argTypes, DataType resultType) throws InternalException, InternalException
        {
            return def;
        }
    }
    
    @OnThread(Tag.Any)
    public abstract <R, E extends Throwable> R apply(DataTypeVisitorEx<R, E> visitor) throws InternalException, E;

    public static class TagType<T>
    {
        private final @ExpressionIdentifier String name;
        private final @Nullable T inner;

        public TagType(@ExpressionIdentifier String name, @Nullable T inner)
        {
            this.name = name;
            this.inner = inner;
        }

        public @ExpressionIdentifier String getName()
        {
            return name;
        }

        @Pure
        public @Nullable T getInner()
        {
            return inner;
        }

        @Override
        public boolean equals(@Nullable Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            TagType tag = (TagType) o;

            if (!name.equals(tag.name)) return false;
            return inner != null ? inner.equals(tag.inner) : tag.inner == null;

        }

        @Override
        public int hashCode()
        {
            int result = name.hashCode();
            result = 31 * result + (inner != null ? inner.hashCode() : 0);
            return result;
        }

        @Override
        public String toString()
        {
            return name + (inner == null ? "" : ("\\" + inner.toString()));
        }

        /*
        public TagType<DataType> upcast()
        {
            return new TagType<>(name, inner);
        }
        */
        
        public <U> TagType<U> map(Function<T, U> changeInner)
        {
            return new TagType<>(name, inner == null ? null : changeInner.apply(inner));
        }

        public <U> TagType<U> mapInt(FunctionInt<T, U> changeInner) throws InternalException
        {
            return new TagType<>(name, inner == null ? null : changeInner.apply(inner));
        }
    }

    @OnThread(Tag.Any)
    @SuppressWarnings("i18n")
    public final @Localized String toDisplay(boolean drillIntoTagged) throws UserException, InternalException
    {
        return apply(new DataTypeVisitor<String>()
        {
            @Override
            public String number(NumberInfo displayInfo) throws InternalException, UserException
            {
                String unit = displayInfo.getUnit().forDisplay();
                return "Number" + (unit.isEmpty() ? "" : "{" + unit + "}");
            }

            @Override
            public String text() throws InternalException, UserException
            {
                return "Text";
            }

            @Override
            public String tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, UserException
            {
                @Localized String typeStr = typeName.getRaw();
                if (!typeVars.isEmpty())
                    typeStr += typeVars.stream().map(t -> "(" + t.either(u -> "{" + u.toString() + "}", ty -> ty.toString()) + ")").collect(Collectors.joining());
                if (drillIntoTagged)
                {
                    typeStr += " <";
                    boolean any = false;
                    for (int i = 0; i < tags.size(); i++)
                    {
                        if (i > 0)
                            typeStr += " | ";
                        TagType<DataType> tag = tags.get(i);
                        typeStr += tag.name;
                        if (tag.inner != null)
                        {
                            typeStr += ":" + tag.inner.toDisplay(true);
                        }
                    }
                    typeStr += ">";
                }
                return typeStr;
            }

            @Override
            public String record(ImmutableMap<@ExpressionIdentifier String, DataType> fields) throws InternalException, UserException
            {
                StringBuilder s = new StringBuilder("(");
                boolean first = true;
                for (Entry<@ExpressionIdentifier String, DataType> field : Utility.iterableStream(fields.entrySet().stream().sorted(Comparator.comparing(e -> e.getKey()))))
                {
                    if (!first)
                        s.append(", ");
                    first = false;
                    s.append(field.getKey() + ": ");
                    s.append(field.getValue().apply(this));
                }
                s.append(")");
                return s.toString();
            }

            @Override
            public String array(@Nullable DataType inner) throws InternalException, UserException
            {
                if (inner == null)
                    return "[]";
                else
                    return "[" + inner.apply(this) + "]";
            }

            @Override
            public String date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
            {
                switch (dateTimeInfo.type)
                {
                    case YEARMONTHDAY:
                        return "Date";
                    case YEARMONTH:
                        return "DateYM";
                    case TIMEOFDAY:
                        return "Time";
                    //case TIMEOFDAYZONED:
                        //return "TimeZoned";
                    case DATETIME:
                        return "DateTime";
                    case DATETIMEZONED:
                        return "DateTimeZoned";
                }
                throw new InternalException("Unknown date type: " + dateTimeInfo.type);
            }

            @Override
            public String bool() throws InternalException, UserException
            {
                return "Boolean";
            }

            @Override
            public String function(ImmutableList<DataType> argTypes, DataType resultType) throws InternalException, UserException
            {
                return "(" + Utility.mapListExI(argTypes, a -> a.toDisplay(drillIntoTagged)).stream().collect(Collectors.joining(", ")) + ") -> " + resultType.toDisplay(drillIntoTagged);
            }
        });
    }

    @Override
    public final String toString()
    {
        try
        {
            return toDisplay(true);
        }
        catch (UserException | InternalException e)
        {
            Log.log(e);
            return "Error: " + e.getLocalizedMessage();
        }
    }

    @Override
    public final StyledString toStyledString()
    {
        try
        {
            return StyledString.s(toDisplay(false));
        }
        catch (UserException | InternalException e)
        {
            Log.log(e);
            return StyledString.s("Error showing type: " + e.getLocalizedMessage());
        }
    }

    // package-visible
    static DataType tagged(TypeId name, ImmutableList<Either<Unit, DataType>> typeVariableSubstitutes, ImmutableList<TagType<DataType>> tagTypes)
    {
        return new TaggedDataType(name, typeVariableSubstitutes, tagTypes);
    }


    public static DataType number(NumberInfo numberInfo)
    {
        return new NumberDataType(numberInfo);
    }

    @Override
    public abstract boolean equals(@Nullable Object o);

    @Override
    public abstract int hashCode();

    // Tries a parse and if it fails, returns null.  Should only be used for single-token parses,
    // or those where you stop trying after failure, because I'm not sure what happens if you're partway
    // through a partial parse and it cancels.
    private static <T> @Nullable T tryParse(Supplier<@Nullable T> parseAction)
    {
        try
        {
            return parseAction.get();
        }
        catch (ParseCancellationException e)
        {
            return null;
        }
    }
    
    private static <DATA_OR_INVALID, T> @Nullable Either<String, T> tryParse(DataParser p, Function<DataParser, DATA_OR_INVALID> parseOrInvalid, Function<DATA_OR_INVALID, InvalidItemContext> getInvalid, Function<DATA_OR_INVALID, T> getValid)
    {
        try
        {
            DATA_OR_INVALID dataOrInvalid = parseOrInvalid.apply(p);
            if (dataOrInvalid != null)
            {
                InvalidItemContext invalidItemContext = getInvalid.apply(dataOrInvalid);
                if (invalidItemContext != null)
                    return Either.left(invalidItemContext.STRING().getText());
                
                T t = getValid.apply(dataOrInvalid);
                if (t != null)
                    return Either.right(t);
            }
            return null;
        }
        catch (ParseCancellationException e)
        {
            return null;
        }
    }

    private static <DATA_OR_INVALID, T> @Nullable Either<String, T> tryParse(DataParser2 p, Function<DataParser2, DATA_OR_INVALID> parseOrInvalid, Function<DATA_OR_INVALID, DataParser2.InvalidItemContext> getInvalid, Function<DATA_OR_INVALID, T> getValid)
    {
        try
        {
            DATA_OR_INVALID dataOrInvalid = parseOrInvalid.apply(p);
            if (dataOrInvalid != null)
            {
                DataParser2.InvalidItemContext invalidItemContext = getInvalid.apply(dataOrInvalid);
                if (invalidItemContext != null)
                    return Either.left(invalidItemContext.STRING().getText());

                T t = getValid.apply(dataOrInvalid);
                if (t != null)
                    return Either.right(t);
            }
            return null;
        }
        catch (ParseCancellationException e)
        {
            return null;
        }
    }

    public static Either<String, @Value ListEx> loadArray(DataType innerFinal, DataParser p) throws UserException, InternalException
    {
        Either<String, OpenSquareContext> openSquare = tryParse(p, DataParser::openSquareOrInvalid, OpenSquareOrInvalidContext::invalidItem, OpenSquareOrInvalidContext::openSquare);
        if (openSquare == null)
            throw new UserException("Expected array but found: \"" + p.getCurrentToken() + "\"");
        return openSquare.<@Value ListEx>mapEx(_s -> {
            List<@Value Object> array = new ArrayList<>();
            boolean seenComma = true;
            while (tryParse(() -> p.closeSquare()) == null)
            {
                if (!seenComma)
                {
                    throw new UserException("Expected comma but found " + p.getCurrentToken());
                }
                array.add(loadSingleItem(innerFinal, p, false).getRight("Invalid nested disallowed"));
                seenComma = tryParse(() -> p.comma()) != null;
            }
            return new ListExList(array);
        });
    }

    public static Either<String, @Value ListEx> loadArray(DataType innerFinal, DataParser2 p) throws UserException, InternalException
    {
        Either<String, DataParser2.OpenSquareContext> openSquare = tryParse(p, DataParser2::openSquareOrInvalid, DataParser2.OpenSquareOrInvalidContext::invalidItem, DataParser2.OpenSquareOrInvalidContext::openSquare);
        if (openSquare == null)
            throw new UserException("Expected array but found: \"" + p.getCurrentToken() + "\"");
        return openSquare.<@Value ListEx>mapEx(_s -> {
            List<@Value Object> array = new ArrayList<>();
            boolean seenComma = true;
            while (tryParse(() -> p.closeSquare()) == null)
            {
                if (!seenComma)
                {
                    throw new UserException("Expected comma but found " + p.getCurrentToken());
                }
                array.add(loadSingleItem(innerFinal, p, false).getRight("Invalid nested disallowed"));
                seenComma = tryParse(() -> p.comma()) != null;
            }
            return new ListExList(array);
        });
    }

    public static Either<String, @Value Record> loadRecord(ImmutableMap<@ExpressionIdentifier String, DataType> fields, DataParser p, boolean consumedRoundBrackets) throws UserException, InternalException
    {
        @Nullable Either<String, Object> openRound;
        if (consumedRoundBrackets)
            openRound = Either.right("");
        else
            openRound = tryParse(p, DataParser::openRoundOrInvalid, OpenRoundOrInvalidContext::invalidItem, OpenRoundOrInvalidContext::openRound);
        if (openRound == null)
            throw new UserException("Expected record but found: \"" + p.getCurrentToken() + "\"");
        return openRound.<@Value Record>mapEx(_o -> {
            Map<@ExpressionIdentifier String, @Value Object> fieldValues = new HashMap<>();
            for (int i = 0; i < fields.size(); i++)
            {
                LabelContext labelContext = p.label();
                @ExpressionIdentifier String fieldName = IdentifierUtility.fromParsed(labelContext);
                if (fieldName == null)
                    throw new ParseException("Invalid field name: \"" + labelContext.getText() + "\"", p);
                DataType fieldType = fields.get(fieldName);
                if (fieldType == null)
                    throw new ParseException("Unrecognised field: \"" + fieldName + "\"", p);
                if (fieldValues.put(fieldName, loadSingleItem(fieldType, p, false).getRight("Invalid nested invalid")) != null)
                    throw new ParseException("Duplicate field: \"" + fieldName + "\"", p);
                if (i < fields.size() - 1)
                {
                    if (tryParse(() -> p.comma()) == null)
                        throw new ParseException("comma", p);
                }
            }
            // Don't actually think this is possible given we check for duplicates and consume the right number, but for sanity:
            if (!fieldValues.keySet().equals(fields.keySet()))
                throw new ParseException("Not all fields found or duplicate fields found", p);
            
            if (!consumedRoundBrackets)
            {
                if (tryParse(() -> p.closeRound()) == null)
                    throw new UserException("Expected tuple end but found: " + p.getCurrentToken());
            }
            return DataTypeUtility.value(new RecordMap(fieldValues));
        });
    }

    public static Either<String, @Value Record> loadRecord(ImmutableMap<@ExpressionIdentifier String, DataType> fields, DataParser2 p, boolean consumedRoundBrackets) throws UserException, InternalException
    {
        @Nullable Either<String, Object> openRound;
        if (consumedRoundBrackets)
            openRound = Either.right("");
        else
            openRound = tryParse(p, DataParser2::openRoundOrInvalid, DataParser2.OpenRoundOrInvalidContext::invalidItem, DataParser2.OpenRoundOrInvalidContext::openRound);
        if (openRound == null)
            throw new UserException("Expected record but found: \"" + p.getCurrentToken() + "\"");
        return openRound.<@Value Record>mapEx(_o -> {
            Map<@ExpressionIdentifier String, @Value Object> fieldValues = new HashMap<>();
            for (int i = 0; i < fields.size(); i++)
            {
                DataParser2.LabelContext labelContext = p.label();
                @ExpressionIdentifier String fieldName = IdentifierUtility.fromParsed(labelContext);
                if (fieldName == null)
                    throw new ParseException("Invalid field name: \"" + labelContext.getText() + "\"", p);
                DataType fieldType = fields.get(fieldName);
                if (fieldType == null)
                    throw new ParseException("Unrecognised field: \"" + fieldName + "\"", p);
                if (fieldValues.put(fieldName, loadSingleItem(fieldType, p, false).getRight("Invalid nested invalid")) != null)
                    throw new ParseException("Duplicate field: \"" + fieldName + "\"", p);
                if (i < fields.size() - 1)
                {
                    if (tryParse(() -> p.comma()) == null)
                        throw new ParseException("comma", p);
                }
            }
            // Don't actually think this is possible given we check for duplicates and consume the right number, but for sanity:
            if (!fieldValues.keySet().equals(fields.keySet()))
                throw new ParseException("Not all fields found or duplicate fields found", p);

            if (!consumedRoundBrackets)
            {
                if (tryParse(() -> p.closeRound()) == null)
                    throw new UserException("Expected tuple end but found: " + p.getCurrentToken());
            }
            return DataTypeUtility.value(new RecordMap(fieldValues));
        });
    }

    public static Either<String, @Value Boolean> loadBool(DataParser p) throws UserException
    {
        Either<String, BoolContext> boolContext = tryParse(p, DataParser::boolOrInvalid, BoolOrInvalidContext::invalidItem, BoolOrInvalidContext::bool);
        if (boolContext == null)
            throw new UserException("Expected boolean value but found: \"" + p.getCurrentToken() + "\"");
        return boolContext.<@Value Boolean>map(b -> DataTypeUtility.value(b.getText().trim().toLowerCase().equals("true")));
    }

    public static Either<String, @Value Boolean> loadBool(DataParser2 p) throws UserException
    {
        Either<String, DataParser2.BoolContext> boolContext = tryParse(p, DataParser2::boolOrInvalid, DataParser2.BoolOrInvalidContext::invalidItem, DataParser2.BoolOrInvalidContext::bool);
        if (boolContext == null)
            throw new UserException("Expected boolean value but found: \"" + p.getCurrentToken() + "\"");
        return boolContext.<@Value Boolean>map(b -> DataTypeUtility.value(b.getText().trim().toLowerCase().equals("true")));
    }

    public static Either<String, @Value String> loadString(DataParser p) throws UserException
    {
        Either<String, StringContext> stringContext = tryParse(p, DataParser::stringOrInvalid, StringOrInvalidContext::invalidItem, StringOrInvalidContext::string);
        if (stringContext == null)
            throw new ParseException("string", p);
        return stringContext.<@Value String>map(string -> DataTypeUtility.value(string.STRING().getText()));
    }

    public static Either<String, @Value String> loadString(DataParser2 p) throws UserException
    {
        Either<String, DataParser2.StringContext> stringContext = tryParse(p, DataParser2::stringOrInvalid, DataParser2.StringOrInvalidContext::invalidItem, DataParser2.StringOrInvalidContext::string);
        if (stringContext == null)
            throw new ParseException("string", p);
        return stringContext.<@Value String>map(string -> DataTypeUtility.value(string.STRING().getText()));
    }

    public static Either<String, @Value Number> loadNumber(DataParser p) throws UserException, InternalException
    {
        Either<String, NumberContext> numberContext = tryParse(p, DataParser::numberOrInvalid, NumberOrInvalidContext::invalidItem, NumberOrInvalidContext::number);
        if (numberContext == null)
            throw new UserException("Expected number value but found: \"" + p.getCurrentToken() + "\"");
        return numberContext.<@Value Number>mapEx(number -> Utility.parseNumber(number.getText().trim()));
    }

    public static Either<String, @Value Number> loadNumber(DataParser2 p) throws UserException, InternalException
    {
        Either<String, DataParser2.NumberContext> numberContext = tryParse(p, DataParser2::numberOrInvalid, DataParser2.NumberOrInvalidContext::invalidItem, DataParser2.NumberOrInvalidContext::number);
        if (numberContext == null)
            throw new UserException("Expected number value but found: \"" + p.getCurrentToken() + "\"");
        return numberContext.<@Value Number>mapEx(number -> Utility.parseNumber(number.getText().trim()));
    }

    public static Either<String, TaggedValue> loadTaggedValue(ImmutableList<TagType<DataType>> tags, DataParser p) throws UserException, InternalException
    {
        Either<String, TagContext> tagContext = tryParse(p, DataParser::tagOrInvalid, TagOrInvalidContext::invalidItem, TagOrInvalidContext::tag);
        if (tagContext == null)
            throw new ParseException("tagged value", p);

        return tagContext.mapEx(b -> {
            String constructor = b.STRING() != null ? b.STRING().getText() : b.UNQUOTED_IDENT().getText();
            for (int i = 0; i < tags.size(); i++)
            {
                TagType<DataType> tag = tags.get(i);
                if (tag.getName().equals(constructor))
                {
                    @Nullable DataType innerType = tag.getInner();
                    if (innerType != null)
                    {
                        if (tryParse(() -> p.openRound()) == null)
                            throw new ParseException("Bracketed inner value for " + constructor, p);
                        @Value Object innerValue = loadSingleItem(innerType, p, true).getRight("Invalid nested invalid");
                        if (tryParse(() -> p.closeRound()) == null)
                            throw new ParseException("Closing tagged value bracket for " + constructor, p);
                        return new TaggedValue(i, innerValue, DataTypeUtility.fromTags(tags));
                    }

                    return new TaggedValue(i, null, DataTypeUtility.fromTags(tags));
                }
            }
            throw new UserException("Could not find matching tag for: \"" + constructor + "\" in: " + tags.stream().map(t -> "\"" + t.getName() + "\"").collect(Collectors.joining(", ")));
        });
    }

    public static Either<String, TaggedValue> loadTaggedValue(ImmutableList<TagType<DataType>> tags, DataParser2 p) throws UserException, InternalException
    {
        Either<String, DataParser2.TagContext> tagContext = tryParse(p, DataParser2::tagOrInvalid, DataParser2.TagOrInvalidContext::invalidItem, DataParser2.TagOrInvalidContext::tag);
        if (tagContext == null)
            throw new ParseException("tagged value", p);

        return tagContext.flatMapEx(b -> {
            String constructor = b.STRING() != null ? b.STRING().getText() : b.UNQUOTED_IDENT().getText();
            for (int i = 0; i < tags.size(); i++)
            {
                TagType<DataType> tag = tags.get(i);
                if (tag.getName().equals(constructor))
                {
                    @Nullable DataType innerType = tag.getInner();
                    if (innerType != null)
                    {
                        if (tryParse(() -> p.openRound()) == null)
                            throw new ParseException("Bracketed inner value for " + constructor, p);
                        @Value Object innerValue = loadSingleItem(innerType, p, true).getRight("Invalid nested invalid");
                        if (tryParse(() -> p.closeRound()) == null)
                            throw new ParseException("Closing tagged value bracket for " + constructor, p);
                        return Either.right(new TaggedValue(i, innerValue, DataTypeUtility.fromTags(tags)));
                    }

                    return Either.right(new TaggedValue(i, null, DataTypeUtility.fromTags(tags)));
                }
            }
            return Either.left(constructor);
        });
    }
    
    public final Either<String, @Value Object> loadSingleItem(String content) throws InternalException
    {
        try
        {
            return Utility.<Either<String, @Value Object>, DataParser2>parseAsOne(content, DataLexer2::new, DataParser2::new, p -> {
                Either<String, @Value Object> item = loadSingleItem(this, p, false);
                p.eof();
                return item;
            });
        }
        catch (UserException e)
        {
            return Either.left(content);
        }
    }

    @OnThread(Tag.Any)
    public static Either<String, @Value Object> loadSingleItem(DataType type, final DataParser p, boolean consumedRoundBrackets) throws InternalException, UserException
    {
        return type.apply(new DataTypeVisitor<Either<String, @Value Object>>()
        {
            @Override
            public Either<String, @Value Object> number(NumberInfo displayInfo) throws InternalException, UserException
            {
                return loadNumber(p).<@Value Object>map(n -> n);
            }

            @Override
            public Either<String, @Value Object> text() throws InternalException, UserException
            {
                return loadString(p).<@Value Object>map(s -> s);
            }

            @Override
            public Either<String, @Value Object> date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
            {
                return dateTimeInfo.parse(p).<@Value Object>map((@Value TemporalAccessor t) -> t);
            }

            @Override
            public Either<String, @Value Object> bool() throws InternalException, UserException
            {
                return loadBool(p).<@Value Object>map(b -> b);
            }

            @Override
            public Either<String, @Value Object> tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, UserException
            {
                return loadTaggedValue(tags, p).<@Value Object>map(t -> t);
            }

            @Override
            public Either<String, @Value Object> record(ImmutableMap<@ExpressionIdentifier String, DataType> fields) throws InternalException, UserException
            {
                return loadRecord(fields, p, consumedRoundBrackets).<@Value Object>map(t -> t);
            }

            @Override
            public Either<String, @Value Object> array(final @Nullable DataType inner) throws InternalException, UserException
            {
                if (inner == null)
                    throw new UserException("Cannot load column with value of type empty array");

                return loadArray(inner, p).<@Value Object>map(l -> l);
            }
        });
    }

    @OnThread(Tag.Any)
    public static Either<String, @Value Object> loadSingleItem(DataType type, final DataParser2 p, boolean consumedRoundBrackets) throws InternalException, UserException
    {
        return type.apply(new DataTypeVisitor<Either<String, @Value Object>>()
        {
            @Override
            public Either<String, @Value Object> number(NumberInfo displayInfo) throws InternalException, UserException
            {
                return loadNumber(p).<@Value Object>map(n -> n);
            }

            @Override
            public Either<String, @Value Object> text() throws InternalException, UserException
            {
                return loadString(p).<@Value Object>map(s -> s);
            }

            @Override
            public Either<String, @Value Object> date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
            {
                return dateTimeInfo.parse(p).<@Value Object>map((@Value TemporalAccessor t) -> t);
            }

            @Override
            public Either<String, @Value Object> bool() throws InternalException, UserException
            {
                return loadBool(p).<@Value Object>map(b -> b);
            }

            @Override
            public Either<String, @Value Object> tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, UserException
            {
                return loadTaggedValue(tags, p).<@Value Object>map(t -> t);
            }

            @Override
            public Either<String, @Value Object> record(ImmutableMap<@ExpressionIdentifier String, DataType> fields) throws InternalException, UserException
            {
                return loadRecord(fields, p, consumedRoundBrackets).<@Value Object>map(t -> t);
            }

            @Override
            public Either<String, @Value Object> array(final @Nullable DataType inner) throws InternalException, UserException
            {
                if (inner == null)
                    throw new UserException("Cannot load column with value of type empty array");

                return loadArray(inner, p).<@Value Object>map(l -> l);
            }
        });
    }


    // save the declaration of this type
    // If topLevelDeclaration is false, save a reference (matters for tagged types)
    public final OutputBuilder save(OutputBuilder b) throws InternalException
    {
        apply(new DataTypeVisitorEx<UnitType, InternalException>()
        {
            @Override
            public UnitType number(NumberInfo numberInfo) throws InternalException, InternalException
            {
                b.t(FormatLexer.NUMBER, FormatLexer.VOCABULARY);
                if (!numberInfo.getUnit().equals(Unit.SCALAR))
                    b.unit(numberInfo.getUnit().toString());
                return UnitType.UNIT;
            }

            @Override
            public UnitType text() throws InternalException, InternalException
            {
                b.t(FormatLexer.TEXT, FormatLexer.VOCABULARY);
                return UnitType.UNIT;
            }

            @Override
            public UnitType date(DateTimeInfo dateTimeInfo) throws InternalException, InternalException
            {
                switch (dateTimeInfo.getType())
                {
                    case YEARMONTHDAY:
                        b.t(FormatLexer.YEARMONTHDAY, FormatLexer.VOCABULARY);
                        break;
                    case YEARMONTH:
                        b.t(FormatLexer.YEARMONTH, FormatLexer.VOCABULARY);
                        break;
                    case TIMEOFDAY:
                        b.t(FormatLexer.TIMEOFDAY, FormatLexer.VOCABULARY);
                        break;
                    //case TIMEOFDAYZONED:
                        //b.t(FormatLexer.TIMEOFDAYZONED, FormatLexer.VOCABULARY);
                        //break;
                    case DATETIME:
                        b.t(FormatLexer.DATETIME, FormatLexer.VOCABULARY);
                        break;
                    case DATETIMEZONED:
                        b.t(FormatLexer.DATETIMEZONED, FormatLexer.VOCABULARY);
                        break;
                }
                return UnitType.UNIT;
            }

            @Override
            public UnitType bool() throws InternalException, InternalException
            {
                b.t(FormatLexer.BOOLEAN, FormatLexer.VOCABULARY);
                return UnitType.UNIT;
            }

            @Override
            @OnThread(value = Tag.Simulation, ignoreParent = true)
            public UnitType tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, InternalException
            {
                if (typeVars.isEmpty())
                {
                    b.raw(typeName.getRaw());
                }
                else
                {
                    b.t(FormatLexer.APPLY, FormatLexer.VOCABULARY);
                    b.raw(" ");
                    b.raw(typeName.getRaw());
                    for (Either<Unit, DataType> typeVar : typeVars)
                    {
                        typeVar.eitherInt(u -> b.unit(u.toString()), t -> {
                            b.raw("(");
                            t.save(b);
                            return b.raw(")");
                        });
                    }
                }
                return UnitType.UNIT;
            }

            @Override
            public UnitType record(ImmutableMap<@ExpressionIdentifier String, DataType> fields) throws InternalException, InternalException
            {
                b.t(FormatLexer.OPEN_BRACKET, FormatLexer.VOCABULARY);
                boolean first = true;
                for (Entry<@ExpressionIdentifier String, DataType> field : Utility.iterableStream(fields.entrySet().stream().sorted(Comparator.comparing(e -> e.getKey()))))
                {
                    if (!first)
                        b.raw(",");
                    first = false;
                    b.expId(field.getKey());
                    b.raw(":");
                    field.getValue().save(b);
                }
                b.t(FormatLexer.CLOSE_BRACKET, FormatLexer.VOCABULARY);
                return UnitType.UNIT;
            }

            @Override
            @OnThread(value = Tag.Simulation, ignoreParent = true)
            public UnitType array(@Nullable DataType inner) throws InternalException, InternalException
            {
                b.t(FormatLexer.OPEN_SQUARE, FormatLexer.VOCABULARY);
                if (inner != null)
                    inner.save(b);
                b.t(FormatLexer.CLOSE_SQUARE, FormatLexer.VOCABULARY);
                return UnitType.UNIT;
            }
        });
        return b;
    }

    public static class DateTimeInfo
    {
        private static final Map<DateTimeType, DateTimeFormatter> STRICT_FORMATTERS = new HashMap<>();
        private static final Map<DateTimeType, ImmutableList<ImmutableList
        <DateTimeFormatter>>> FLEXIBLE_FORMATTERS = new HashMap<>();

        public DateTimeFormatter getStrictFormatter()
        {
            return STRICT_FORMATTERS.computeIfAbsent(getType(), DateTimeInfo::makeFormatter);
        }
        
        // Each inner list is a grouping that is likely to have
        // multiple matches and thus show up an ambiguous parse.
        public ImmutableList<ImmutableList<DateTimeFormatter>> getFlexibleFormatters()
        {
            // We can't use computeIfAbsent because it causes ConcurrentModificationException:
            ImmutableList<ImmutableList<DateTimeFormatter>> existing = FLEXIBLE_FORMATTERS.get(getType());
            if (existing != null)
                return existing;
            ImmutableList<ImmutableList<DateTimeFormatter>> calc = makeFlexibleFormatter(getType());
            FLEXIBLE_FORMATTERS.put(getType(), calc);
            return calc;
            
        }
        private ImmutableList<ImmutableList<DateTimeFormatter>> makeFlexibleFormatter(DateTimeType type)
        {
            // Shared among some branches:
            ImmutableList.Builder<ImmutableList<DateTimeFormatter>> r = ImmutableList.builder();
            switch (type)
            {
                case YEARMONTHDAY:
                    // All the formats here use space as a separator, and assume that
                    // the items have been fed through the pre-process function in here.
                    return ImmutableList.of(
                        l(m(" ", DAY, MONTH_TEXT_SHORT, YEAR4)), // dd MMM yyyy
                        l(m(" ", DAY, MONTH_TEXT_LONG, YEAR4)), // dd MMM yyyy

                        l(m(" ", MONTH_TEXT_SHORT, DAY, YEAR4)), // MMM dd yyyy
                        l(m(" ", MONTH_TEXT_LONG, DAY, YEAR4)), // MMM dd yyyy

                        l(m(" ", YEAR4, MONTH_TEXT_SHORT, DAY)), // yyyy MMM dd

                        l(m(" ", YEAR4, MONTH_NUM, DAY)), // yyyy MM dd

                        l(m(" ", DAY, MONTH_NUM, YEAR4), m(" ", MONTH_NUM, DAY, YEAR4)), // dd MM yyyy or MM dd yyyy

                        l(m(" ", DAY, MONTH_NUM, YEAR2), m(" ", MONTH_NUM, DAY, YEAR2)) // dd MM yy or MM dd yy
                    );
                case TIMEOFDAY:
                    return ImmutableList.of(
                        l(m(":", HOUR, MIN, SEC_OPT_FRAC_OPT)), // HH:mm[:ss[.S]]
                        l(m(":", HOUR12, MIN, SEC_OPT_FRAC_OPT, AMPM)) // hh:mm[:ss[.S]] PM
                    );
                case DATETIME:
                    for (List<DateTimeFormatter> timeFormats : new DateTimeInfo(DateTimeType.TIMEOFDAY).getFlexibleFormatters())
                    {
                        for (List<DateTimeFormatter> dateFormats : new DateTimeInfo(DateTimeType.YEARMONTHDAY).getFlexibleFormatters())
                        {
                            ImmutableList<DateTimeFormatter> newFormatsSpace = Utility.allPairs(dateFormats, timeFormats, (d, t) -> new DateTimeFormatterBuilder().append(d).appendLiteral(" ").append(t).toFormatter());
                            ImmutableList<DateTimeFormatter> newFormatsT = Utility.allPairs(dateFormats, timeFormats, (d, t) -> new DateTimeFormatterBuilder().append(d).appendLiteral("T").append(t).toFormatter());
                            r.add(newFormatsSpace);
                            r.add(newFormatsT);
                        }
                    }
                    return r.build();
                case YEARMONTH:
                    return ImmutableList.of(
                        l(m(" ", F.MONTH_NUM, F.YEAR4)),
                        l(m(" ", F.MONTH_TEXT_SHORT, F.YEAR2)),
                        l(m(" ", F.MONTH_TEXT_SHORT, F.YEAR4)),
                        l(m(" ", F.MONTH_TEXT_LONG, F.YEAR4)),
                        l(m(" ", F.YEAR4, F.MONTH_NUM))
                    );
                case DATETIMEZONED:
                    for (ImmutableList<DateTimeFormatter> dateTimeFormats : new DateTimeInfo(DateTimeType.DATETIME).getFlexibleFormatters())
                    {
                        r.add(Utility.<DateTimeFormatter, DateTimeFormatter>mapListI(dateTimeFormats, dateTimeFormat -> {
                            DateTimeFormatterBuilder b = new DateTimeFormatterBuilder().append(dateTimeFormat);
                            b.optionalStart().appendLiteral(" ").optionalEnd();
                            b.appendZoneRegionId();
                            return b.toFormatter();
                        }));
                        r.add(Utility.<DateTimeFormatter, DateTimeFormatter>mapListI(dateTimeFormats, dateTimeFormat -> {
                            DateTimeFormatterBuilder b = new DateTimeFormatterBuilder().append(dateTimeFormat);
                            b.optionalStart().appendLiteral(" ").optionalEnd();
                            b.appendZoneText(TextStyle.SHORT);
                            return b.toFormatter();
                        }));
                        r.add(Utility.<DateTimeFormatter, DateTimeFormatter>mapListI(dateTimeFormats, dateTimeFormat -> {
                            DateTimeFormatterBuilder b = new DateTimeFormatterBuilder().append(dateTimeFormat);
                            b.optionalStart().appendLiteral(" ").optionalEnd();
                            b.appendOffsetId().appendLiteral("[").appendZoneRegionId().appendLiteral("]");
                            return b.toFormatter();
                        }));
                    }
                    return r.build();
                        /*
                    case TIMEOFDAYZONED:
                        for (List<DateTimeFormatter> formatters : new DateTimeInfo(DateTimeType.TIMEOFDAY).getFlexibleFormatters())
                        {
                            ImmutableList.Builder<DateTimeFormatter> inner = ImmutableList.builder();
                            for (DateTimeFormatter dateTimeFormat : formatters)
                            {
                                // Since we don't have a date, a named zone doesn't help, only a specific
                                // offset time is valid:
                                DateTimeFormatterBuilder b = new DateTimeFormatterBuilder().append(dateTimeFormat);
                                b.optionalStart().appendLiteral(" ").optionalEnd();
                                b.appendOffsetId();
                                inner.add(b.toFormatter());
                            }
                            r.add(inner.build());
                        }
                        return r.build();
                        */
            }
            return ImmutableList.<ImmutableList<DateTimeFormatter>>of(ImmutableList.<DateTimeFormatter>of(getStrictFormatter()));

        }

        // public for testing
        public static enum F {SEC_OPT_FRAC_OPT, MIN, HOUR, HOUR12, AMPM, DAY, MONTH_TEXT_SHORT, MONTH_TEXT_LONG, MONTH_NUM, YEAR2, YEAR4 }

        // public for testing
        public static DateTimeFormatter m(String sep, F... items)
        {
            DateTimeFormatterBuilder b = new DateTimeFormatterBuilder();
            for (int i = 0; i < items.length; i++)
            {
                switch (items[i])
                {
                    case SEC_OPT_FRAC_OPT:
                        b.optionalStart();
                        if (i != 0) b.appendLiteral(sep);
                        b.appendValue(ChronoField.SECOND_OF_MINUTE, 2, 2, SignStyle.NEVER);
                        b.optionalStart();
                        // From http://stackoverflow.com/questions/30090710/java-8-datetimeformatter-parsing-for-optional-fractional-seconds-of-varying-sign
                        b.appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true);
                        b.optionalEnd();
                        b.optionalEnd();
                        break;
                    case MIN:
                        if (i != 0) b.appendLiteral(sep);
                        b.appendValue(ChronoField.MINUTE_OF_HOUR, 2, 2, SignStyle.NEVER);
                        break;
                    case HOUR:
                        b.appendValue(ChronoField.HOUR_OF_DAY, 1, 2, SignStyle.NEVER);
                        break;
                    case HOUR12:
                        b.appendValue(ChronoField.CLOCK_HOUR_OF_AMPM, 1, 2, SignStyle.NEVER);
                        break;
                    case AMPM:
                        b.parseCaseInsensitive()
                         .optionalStart()
                         .appendLiteral(" ")
                         .optionalEnd()
                         .appendText(ChronoField.AMPM_OF_DAY)
                         .parseCaseSensitive();
                        break;
                    case DAY:
                        if (i != 0) b.appendLiteral(sep);
                        b.appendValue(ChronoField.DAY_OF_MONTH, 1, 2, SignStyle.NEVER);
                        break;
                    case MONTH_TEXT_SHORT:
                        if (i != 0) b.appendLiteral(sep);
                        b.parseCaseInsensitive().appendText(ChronoField.MONTH_OF_YEAR, TextStyle.SHORT).parseCaseSensitive();
                        break;
                    case MONTH_TEXT_LONG:
                        if (i != 0) b.appendLiteral(sep);
                        b.parseCaseInsensitive().appendText(ChronoField.MONTH_OF_YEAR, TextStyle.FULL).parseCaseSensitive();
                        break;
                    case MONTH_NUM:
                        if (i != 0) b.appendLiteral(sep);
                        b.appendValue(ChronoField.MONTH_OF_YEAR, 1, 2, SignStyle.NEVER);
                        break;
                    case YEAR2:
                        if (i != 0) b.appendLiteral(sep);
                        // From http://stackoverflow.com/questions/29490893/parsing-string-to-local-date-doesnt-use-desired-century
                        b.appendValueReduced(ChronoField.YEAR, 2, 2, Year.now().getValue() - 80);
                        break;
                    case YEAR4:
                        if (i != 0) b.appendLiteral(sep);
                        b.appendValue(ChronoField.YEAR, 4, 4, SignStyle.NEVER);
                        break;
                }
            }
            return b.toFormatter();
        }

        static ImmutableList<DateTimeFormatter> l(DateTimeFormatter... args)
        {
            return ImmutableList.copyOf(args);
        }


        private static DateTimeFormatter makeFormatter(DateTimeType type)
        {
            DateTimeFormatterBuilder builder = new DateTimeFormatterBuilder();
            if (type.hasYearMonth())
            {
                builder.appendValue(YEAR, 4, 10, SignStyle.EXCEEDS_PAD)
                    .appendLiteral('-')
                    .appendValue(MONTH_OF_YEAR, 2);
            }
            if (type.hasDay())
                builder.appendLiteral('-').appendValue(DAY_OF_MONTH, 2);
            if ((type.hasYearMonth() || type.hasDay()) && type.hasTime())
                builder.appendLiteral(' ');
            if (type.hasTime())
                builder.appendValue(HOUR_OF_DAY, 2)
                    .appendLiteral(':')
                    .appendValue(MINUTE_OF_HOUR, 2)
                    .optionalStart()
                    .appendLiteral(':')
                    .appendValue(SECOND_OF_MINUTE, 2)
                    .optionalStart()
                    .appendFraction(NANO_OF_SECOND, 0, 9, true)
                    .optionalEnd()
                    .optionalEnd();
            if (type.hasZoneOffset())
                builder.appendOffsetId();
            if (type.hasZoneId())
                builder
                    .appendLiteral(' ')
                    .parseCaseSensitive()
                    .appendZoneOrOffsetId();
            return builder.toFormatter();
        }

        /**
         * This parses in two senses.  First, it parses using the provided DataParser.
         * We don't actually use that directly to get the value info out, instead
         * feeling it to the formatter.  This may seem odd, but we already have the formatter.
         * However, the parser needs to know what data tokens got consumed to parse
         * the next item, which is not easily extracted out of the data/time formatter.
         * Hence this slightly odd double layer.
         * @return
         */
        public Either<String, @Value TemporalAccessor> parse(DataParser p) throws UserException, InternalException
        {
            Either<String, ParserRuleContext> parsed;
            switch (type)
            {
                case YEARMONTHDAY:
                    parsed = tryParse(p, DataParser::ymdOrInvalid, YmdOrInvalidContext::invalidItem, YmdOrInvalidContext::ymd);
                    break;
                case YEARMONTH:
                    parsed = tryParse(p, DataParser::ymOrInvalid, YmOrInvalidContext::invalidItem, YmOrInvalidContext::ym);
                    break;
                case TIMEOFDAY:
                    parsed = tryParse(p, DataParser::localTimeOrInvalid, LocalTimeOrInvalidContext::invalidItem, LocalTimeOrInvalidContext::localTime);
                    break;
                //case TIMEOFDAYZONED:
                    //return p.offsetTime();
                case DATETIME:
                    parsed = tryParse(p, DataParser::localDateTimeOrInvalid, LocalDateTimeOrInvalidContext::invalidItem, LocalDateTimeOrInvalidContext::localDateTime);
                    break;
                case DATETIMEZONED:
                    parsed = tryParse(p, DataParser::zonedDateTimeOrInvalid, ZonedDateTimeOrInvalidContext::invalidItem, ZonedDateTimeOrInvalidContext::zonedDateTime);
                    break;
                default:
                    throw new InternalException("Unrecognised format: " + type);
            }
            if (parsed == null)
                throw new ParseException("Date value ", p);
            return parsed.<@Value TemporalAccessor>mapEx(c -> {
                DateTimeFormatter formatter = getStrictFormatter();
                try
                {
                    return fromParsed(formatter.parse(c.getText().trim()));
                }
                catch (DateTimeParseException e)
                {
                    throw new UserException("Problem reading date/time of type " + getType() + " from {" + c.getText() + "}", e);
                }
            });
        }

        /**
         * This parses in two senses.  First, it parses using the provided DataParser.
         * We don't actually use that directly to get the value info out, instead
         * feeling it to the formatter.  This may seem odd, but we already have the formatter.
         * However, the parser needs to know what data tokens got consumed to parse
         * the next item, which is not easily extracted out of the data/time formatter.
         * Hence this slightly odd double layer.
         * @return
         */
        public Either<String, @Value TemporalAccessor> parse(DataParser2 p) throws UserException, InternalException
        {
            Either<String, ParserRuleContext> parsed;
            switch (type)
            {
                case YEARMONTHDAY:
                    parsed = tryParse(p, DataParser2::ymdOrInvalid, DataParser2.YmdOrInvalidContext::invalidItem, DataParser2.YmdOrInvalidContext::ymd);
                    break;
                case YEARMONTH:
                    parsed = tryParse(p, DataParser2::ymOrInvalid, DataParser2.YmOrInvalidContext::invalidItem, DataParser2.YmOrInvalidContext::ym);
                    break;
                case TIMEOFDAY:
                    parsed = tryParse(p, DataParser2::localTimeOrInvalid, DataParser2.LocalTimeOrInvalidContext::invalidItem, DataParser2.LocalTimeOrInvalidContext::localTime);
                    break;
                //case TIMEOFDAYZONED:
                //return p.offsetTime();
                case DATETIME:
                    parsed = tryParse(p, DataParser2::localDateTimeOrInvalid, DataParser2.LocalDateTimeOrInvalidContext::invalidItem, DataParser2.LocalDateTimeOrInvalidContext::localDateTime);
                    break;
                case DATETIMEZONED:
                    parsed = tryParse(p, DataParser2::zonedDateTimeOrInvalid, DataParser2.ZonedDateTimeOrInvalidContext::invalidItem, DataParser2.ZonedDateTimeOrInvalidContext::zonedDateTime);
                    break;
                default:
                    throw new InternalException("Unrecognised format: " + type);
            }
            if (parsed == null)
                throw new ParseException("Date value ", p);
            return parsed.<@Value TemporalAccessor>mapEx(c -> {
                DateTimeFormatter formatter = getStrictFormatter();
                try
                {
                    return fromParsed(formatter.parse(c.getText().trim()));
                }
                catch (DateTimeParseException e)
                {
                    throw new UserException("Problem reading date/time of type " + getType() + " from {" + c.getText() + "}", e);
                }
            });
        }


        public static enum DateTimeType
        {
            /** LocalDate */
            YEARMONTHDAY,
            /** YearMonth */
            YEARMONTH,
            /** LocalTime */
            TIMEOFDAY,
            /** OffsetTime */
            //TIMEOFDAYZONED,
            /** LocalDateTime */
            DATETIME,
            /** ZonedDateTime */
            DATETIMEZONED;

            public boolean hasDay()
            {
                switch (this)
                {
                    case YEARMONTHDAY:
                    case DATETIME:
                    case DATETIMEZONED:
                        return true;
                    default:
                        return false;
                }
            }

            public boolean hasYearMonth()
            {
                switch (this)
                {
                    case YEARMONTH:
                    case YEARMONTHDAY:
                    case DATETIME:
                    case DATETIMEZONED:
                        return true;
                    default:
                        return false;
                }
            }


            public boolean hasTime()
            {
                switch (this)
                {
                    case TIMEOFDAY:
                    //case TIMEOFDAYZONED:
                    case DATETIME:
                    case DATETIMEZONED:
                        return true;
                    default:
                        return false;
                }
            }

            public String literalPrefix()
            {
                switch (this)
                {
                    case YEARMONTHDAY:
                        return "date";
                    case YEARMONTH:
                        return "dateym";
                    case TIMEOFDAY:
                        return "time";
                    case DATETIME:
                        return "datetime";
                    case DATETIMEZONED:
                        return "datetimezoned";
                }
                // Should never happen:
                return "";
            }

            public boolean hasZoneOffset()
            {
                return false; //this == TIMEOFDAYZONED;
            }

            public boolean hasZoneId()
            {
                return this == DATETIMEZONED;
            }
        }

        private final DateTimeType type;

        public DateTimeInfo(DateTimeType type)
        {
            this.type = type;
        }

        public static final TemporalAccessor DEFAULT_VALUE = ZonedDateTime.of(1900, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC"));

        public @ImmediateValue TemporalAccessor getDefaultValue() throws InternalException
        {
            switch (type)
            {
                case YEARMONTHDAY:
                    return LocalDate.from(DEFAULT_VALUE);
                case YEARMONTH:
                    return YearMonth.from(DEFAULT_VALUE);
                case TIMEOFDAY:
                    return LocalTime.from(DEFAULT_VALUE);
                //case TIMEOFDAYZONED:
                //    return OffsetTime.from(DEFAULT_VALUE);
                case DATETIME:
                    return LocalDateTime.from(DEFAULT_VALUE);
                case DATETIMEZONED:
                    return  ZonedDateTime.from(DEFAULT_VALUE);
            }
            throw new InternalException("Unknown type: " + type);
        }

        public @Value TemporalAccessor fromParsed(TemporalAccessor t) throws InternalException
        {
            @Value TemporalAccessor r = DataTypeUtility.value(this, t);
            if (r != null)
                return r;
            else
                throw new InternalException("Error loading date: " + t);
            
        }

        public DateTimeType getType()
        {
            return type;
        }

        public boolean sameType(@Nullable DateTimeInfo dateTimeInfo)
        {
            return dateTimeInfo != null && type == dateTimeInfo.type;
        }

        public int hashCodeForType()
        {
            return type.hashCode();
        }

        @Override
        public boolean equals(@Nullable Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DateTimeInfo that = (DateTimeInfo) o;
            return type == that.type;
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(type);
        }

        /**
         * Gets a comparator for this particular temporal type.
         * @param pool    If true, give a comparator suitable for object pools.  Here, we should only
         *                return zero iff equals(..) is true and the values are completely equal.
         *                If false, give a comparator for user
         *                code.  You can particularly see the difference for OffsetTime, where
         *                11:21+00:00 and 10:21-01:00 are equals for user comparison purposes, but not
         *                for the object pool, since they are not the same value and need to be stored
         *                differently.
         * @return
         */
        public Comparator<@UnknownIfValue TemporalAccessor> getComparator(boolean pool) throws InternalException
        {
            switch (type)
            {
                case YEARMONTHDAY:
                    return Comparator.comparing((TemporalAccessor t) -> t.get(ChronoField.YEAR))
                        .thenComparing((TemporalAccessor t) -> t.get(ChronoField.MONTH_OF_YEAR))
                        .thenComparing((TemporalAccessor t) -> t.get(ChronoField.DAY_OF_MONTH));
                case YEARMONTH:
                    return Comparator.comparing((TemporalAccessor t) -> t.get(ChronoField.YEAR))
                        .thenComparing((TemporalAccessor t) -> t.get(ChronoField.MONTH_OF_YEAR));
                case TIMEOFDAY:
                    return Comparator.comparing((TemporalAccessor t) -> t.get(ChronoField.HOUR_OF_DAY))
                        .thenComparing((TemporalAccessor t) -> t.get(ChronoField.MINUTE_OF_HOUR))
                        .thenComparing((TemporalAccessor t) -> t.get(ChronoField.SECOND_OF_MINUTE))
                        .thenComparing((TemporalAccessor t) -> t.get(ChronoField.NANO_OF_SECOND));
                /*
                case TIMEOFDAYZONED:
                    return Comparator.comparing((TemporalAccessor t) -> {
                            if (pool)
                                return OffsetTime.from(t);
                            else
                                return OffsetTime.from(t).withOffsetSameInstant(ZoneOffset.UTC);
                        },
                        Comparator.comparing((TemporalAccessor t) -> t.get(ChronoField.HOUR_OF_DAY))
                            .thenComparing((TemporalAccessor t) -> t.get(ChronoField.MINUTE_OF_HOUR))
                            .thenComparing((TemporalAccessor t) -> t.get(ChronoField.SECOND_OF_MINUTE))
                            .thenComparing((TemporalAccessor t) -> t.get(ChronoField.NANO_OF_SECOND))
                            .thenComparing((TemporalAccessor t) -> t.get(ChronoField.OFFSET_SECONDS))
                    );
                */
                case DATETIME:
                    return Comparator.comparing((TemporalAccessor t) -> t.get(ChronoField.YEAR))
                        .thenComparing((TemporalAccessor t) -> t.get(ChronoField.MONTH_OF_YEAR))
                        .thenComparing((TemporalAccessor t) -> t.get(ChronoField.DAY_OF_MONTH))
                        .thenComparing((TemporalAccessor t) -> t.get(ChronoField.HOUR_OF_DAY))
                        .thenComparing((TemporalAccessor t) -> t.get(ChronoField.MINUTE_OF_HOUR))
                        .thenComparing((TemporalAccessor t) -> t.get(ChronoField.SECOND_OF_MINUTE))
                        .thenComparing((TemporalAccessor t) -> t.get(ChronoField.NANO_OF_SECOND));
                case DATETIMEZONED:
                    return Comparator.comparing((TemporalAccessor t) -> {
                            if (pool)
                                return ZonedDateTime.from(t);
                            else
                                return ZonedDateTime.from(t).withZoneSameInstant(ZoneOffset.UTC);

                        },
                        Comparator.comparing((TemporalAccessor t) -> t.get(ChronoField.YEAR))
                            .thenComparing((TemporalAccessor t) -> t.get(ChronoField.MONTH_OF_YEAR))
                            .thenComparing((TemporalAccessor t) -> t.get(ChronoField.DAY_OF_MONTH))
                            .thenComparing((TemporalAccessor t) -> t.get(ChronoField.HOUR_OF_DAY))
                            .thenComparing((TemporalAccessor t) -> t.get(ChronoField.MINUTE_OF_HOUR))
                            .thenComparing((TemporalAccessor t) -> t.get(ChronoField.SECOND_OF_MINUTE))
                            .thenComparing((TemporalAccessor t) -> t.get(ChronoField.NANO_OF_SECOND))
                            .<String>thenComparing((TemporalAccessor t) -> ZonedDateTime.from(t).getZone().toString())
                    );
            }
            throw new InternalException("Unknown date type: " + type);
        }

        @Override
        public String toString()
        {
            return "DateTimeInfo{" +
                "type=" + type +
                '}';
        }
    }
    
    private static final class NumberDataType extends DataType
    {
        private final NumberInfo numberInfo;

        public NumberDataType(NumberInfo numberInfo)
        {
            this.numberInfo = numberInfo;
        }

        @Override
        public boolean equals(@Nullable Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            NumberDataType that = (NumberDataType) o;
            return numberInfo.equals(that.numberInfo);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(numberInfo);
        }

        @Override
        public <R, E extends Throwable> @OnThread(Tag.Any) R apply(DataTypeVisitorEx<R, E> visitor) throws InternalException, E
        {
            return visitor.number(numberInfo);
        }
    }
    private static final class TextDataType extends DataType
    {
        @Override
        public boolean equals(@Nullable Object o)
        {
            return o instanceof TextDataType;
        }

        @Override
        public int hashCode()
        {
            return 1;
        }

        @Override
        public <R, E extends Throwable> @OnThread(Tag.Any) R apply(DataTypeVisitorEx<R, E> visitor) throws InternalException, E
        {
            return visitor.text();
        }
    }
    private static final class BooleanDataType extends DataType
    {
        @Override
        public boolean equals(@Nullable Object o)
        {
            return o instanceof BooleanDataType;
        }

        @Override
        public int hashCode()
        {
            return 1;
        }

        @Override
        public <R, E extends Throwable> @OnThread(Tag.Any) R apply(DataTypeVisitorEx<R, E> visitor) throws InternalException, E
        {
            return visitor.bool();
        }
    }
    private static final class TemporalDataType extends DataType
    {
        private final DateTimeInfo dateTimeInfo;

        public TemporalDataType(DateTimeInfo dateTimeInfo)
        {
            this.dateTimeInfo = dateTimeInfo;
        }

        @Override
        public boolean equals(@Nullable Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TemporalDataType that = (TemporalDataType) o;
            return dateTimeInfo.equals(that.dateTimeInfo);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(dateTimeInfo);
        }

        @Override
        public <R, E extends Throwable> @OnThread(Tag.Any) R apply(DataTypeVisitorEx<R, E> visitor) throws InternalException, E
        {
            return visitor.date(dateTimeInfo);
        }
    }
    private static final class TaggedDataType extends DataType
    {
        private final TypeId name;
        private final ImmutableList<Either<Unit, DataType>> typeVariableSubstitutions;
        private final ImmutableList<TagType<DataType>> tagTypes;

        protected TaggedDataType(TypeId name, ImmutableList<Either<Unit, DataType>> typeVariableSubstitutions, ImmutableList<TagType<DataType>> tagTypes)
        {
            this.name = name;
            this.typeVariableSubstitutions = typeVariableSubstitutions;
            this.tagTypes = tagTypes;
        }

        @Override
        public boolean equals(@Nullable Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TaggedDataType that = (TaggedDataType) o;
            return name.equals(that.name) &&
                typeVariableSubstitutions.equals(that.typeVariableSubstitutions) &&
                tagTypes.equals(that.tagTypes);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(name, typeVariableSubstitutions, tagTypes);
        }

        @Override
        public <R, E extends Throwable> @OnThread(Tag.Any) R apply(DataTypeVisitorEx<R, E> visitor) throws InternalException, E
        {
            return visitor.tagged(name, typeVariableSubstitutions, tagTypes);
        }
    }
    private static final class RecordDataType extends DataType
    {
        private final ImmutableMap<@ExpressionIdentifier String, DataType> fields;

        public RecordDataType(Map<@ExpressionIdentifier String, DataType> fields)
        {
            this.fields = ImmutableMap.copyOf(fields);
        }

        @Override
        public boolean equals(@Nullable Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RecordDataType that = (RecordDataType) o;
            return fields.equals(that.fields);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(fields);
        }

        @Override
        public <R, E extends Throwable> @OnThread(Tag.Any) R apply(DataTypeVisitorEx<R, E> visitor) throws InternalException, E
        {
            return visitor.record(fields);
        }
    }
    private static final class ListDataType extends DataType
    {
        private final DataType inner;

        private ListDataType(DataType inner)
        {
            this.inner = inner;
        }

        @Override
        public boolean equals(@Nullable Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ListDataType that = (ListDataType) o;
            return inner.equals(that.inner);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(inner);
        }

        @Override
        public <R, E extends Throwable> @OnThread(Tag.Any) R apply(DataTypeVisitorEx<R, E> visitor) throws InternalException, E
        {
            return visitor.array(inner);
        }
    }
    private static final class FunctionDataType extends DataType
    {
        private final ImmutableList<DataType> argType;
        private final DataType resultType;
        
        public FunctionDataType(ImmutableList<DataType> argType, DataType resultType)
        {
            this.argType = argType;
            this.resultType = resultType;
        }

        @Override
        public boolean equals(@Nullable Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FunctionDataType that = (FunctionDataType) o;
            return argType.equals(that.argType) &&
                resultType.equals(that.resultType);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(argType, resultType);
        }

        @Override
        public <R, E extends Throwable> @OnThread(Tag.Any) R apply(DataTypeVisitorEx<R, E> visitor) throws InternalException, E
        {
            return visitor.function(argType, resultType);
        }
    }
}
