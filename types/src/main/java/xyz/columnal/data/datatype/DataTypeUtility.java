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
import annotation.identifier.qual.UnitIdentifier;
import annotation.qual.ImmediateValue;
import annotation.qual.UnknownIfValue;
import annotation.qual.Value;
import annotation.userindex.qual.UserIndex;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import one.util.streamex.StreamEx;
import org.checkerframework.checker.nullness.qual.Nullable;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.data.datatype.DataType.DataTypeVisitorEx;
import xyz.columnal.data.datatype.DataType.DateTimeInfo;
import xyz.columnal.data.datatype.DataType.DateTimeInfo.DateTimeType;
import xyz.columnal.data.datatype.DataType.FlatDataTypeVisitor;
import xyz.columnal.data.datatype.DataType.SpecificDataTypeVisitor;
import xyz.columnal.data.datatype.DataType.TagType;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.grammar.GrammarUtility;
import xyz.columnal.log.Log;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.ParseProgress;
import xyz.columnal.utility.TaggedValue;
import xyz.columnal.utility.TaggedValue.TaggedTypeDefinitionBase;
import xyz.columnal.utility.UnitType;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Utility.ListEx;
import xyz.columnal.utility.Utility.ListExList;
import xyz.columnal.utility.Utility.Record;
import xyz.columnal.utility.Utility.RecordMap;
import xyz.columnal.utility.Utility.ValueFunctionBase;
import xyz.columnal.utility.Utility.WrappedCharSequence;
import xyz.columnal.utility.Workers;
import xyz.columnal.utility.Workers.Priority;

import java.math.BigDecimal;
import java.text.ParsePosition;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Created by neil on 22/11/2016.
 */
public class DataTypeUtility
{
    /*
    public static @Value Object generateExample(DataType type, int index) throws UserException, InternalException
    {
        return type.apply(new DataTypeVisitor<@Value Object>()
        {

            @Override
            public @Value Object number(NumberInfo displayInfo) throws InternalException, UserException
            {
                return value((long)index);
            }

            @Override
            public @Value Object text() throws InternalException, UserException
            {
                return value(Arrays.asList("Aardvark", "Bear", "Cat", "Dog", "Emu", "Fox").get(index));
            }

            @Override
            public @Value Object bool() throws InternalException, UserException
            {
                return value((index % 2) == 1);
            }

            @Override
            public @Value Object date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
            {
                return value(dateTimeInfo, LocalDate.ofEpochDay(index));
            }

            @Override
            public @Value TaggedValue tagged(TypeId typeName, ImmutableList<DataType> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, UserException
            {
                int tag = index % tags.size();
                @Nullable DataType inner = tags.get(tag).getInner();
                if (inner != null)
                    return new TaggedValue(tag, generateExample(inner, index - tag));
                else
                    return new TaggedValue(tag, null);
            }

            @Override
            public @Value Object tuple(ImmutableList<DataType> inner) throws InternalException, UserException
            {
                return value(Utility.<DataType, @Value Object>mapListEx(inner, t -> generateExample(t, index)).toArray(new @Value Object[0]));
            }

            @Override
            public @Value Object array(@Nullable DataType inner) throws InternalException, UserException
            {
                return value(Collections.emptyList());
            }
        });
    }
    */


    public static @Value int requireInteger(@Value Object o) throws UserException, InternalException
    {
        return Utility.<@Value Integer>withNumber(o, l -> {
            if (l.longValue() != l.intValue())
                throw new UserException("Number too large: " + l);
            return value(l.intValue());
        }, bd -> {
            try
            {
                return value(bd.intValueExact());
            }
            catch (ArithmeticException e)
            {
                throw new UserException("Number not an integer or too large: " + bd);
            }
        });
    }

    @SuppressWarnings("userindex")
    public static @UserIndex @Value int userIndex(@Value Object value) throws InternalException, UserException
    {
        @Value int integer = requireInteger(value);
        return integer;
    }

    //@SuppressWarnings("valuetype")
    public static <T> @UnknownIfValue T unvalue(@Value T v)
    {
        return v;
    }

    @SuppressWarnings("valuetype")
    public static @ImmediateValue Byte value(@UnknownIfValue Byte number)
    {
        return number;
    }

    @SuppressWarnings("valuetype")
    public static @ImmediateValue Short value(@UnknownIfValue Short number)
    {
        return number;
    }

    @SuppressWarnings("valuetype")
    public static @ImmediateValue Integer value(@UnknownIfValue Integer number)
    {
        return number;
    }

    @SuppressWarnings("valuetype")
    public static @ImmediateValue BigDecimal value(@UnknownIfValue BigDecimal number)
    {
        return number;
    }

    @SuppressWarnings("valuetype")
    public static @ImmediateValue Long value(@UnknownIfValue Long number)
    {
        return number;
    }

    @SuppressWarnings("valuetype")
    public static @ImmediateValue Boolean value(@UnknownIfValue Boolean bool)
    {
        return bool;
    }

    @SuppressWarnings("valuetype")
    public static @ImmediateValue String value(@UnknownIfValue String string)
    {
        return string;
    }

    @SuppressWarnings("valuetype")
    public static @Value LocalDate valueDate(LocalDate t)
    {
        return t;
    }

    @SuppressWarnings("valuetype")
    public static @Value LocalTime valueTime(LocalTime t)
    {
        return t;
    }

    @SuppressWarnings("valuetype")
    public static @Value ZonedDateTime valueZonedDateTime(ZonedDateTime t)
    {
        return t;
    }
    
    @SuppressWarnings("valuetype")
    public static @Nullable @ImmediateValue TemporalAccessor value(DateTimeInfo dest, @UnknownIfValue TemporalAccessor t)
    {
        try
        {

            switch (dest.getType())
            {
                case YEARMONTHDAY:
                    if (t instanceof LocalDate)
                        return t;
                    else
                        return LocalDate.from(t);
                case YEARMONTH:
                    if (t instanceof YearMonth)
                        return t;
                    else
                        return YearMonth.from(t);
                case TIMEOFDAY:
                    if (t instanceof LocalTime)
                        return t;
                    else
                        return LocalTime.from(t);
            /*
            case TIMEOFDAYZONED:
                if (t instanceof OffsetTime)
                    return t;
                else
                    return OffsetTime.from(t);
            */
                case DATETIME:
                    if (t instanceof LocalDateTime)
                        return t;
                    else if (t instanceof LocalDate) // This is mainly for R import help
                        return LocalDateTime.of((LocalDate)t, LocalTime.MIN);
                    else
                        return LocalDateTime.from(t);
                case DATETIMEZONED:
                    if (t instanceof ZonedDateTime)
                        return t;
                    else
                        return ZonedDateTime.from(t);
            }
        }
        catch (DateTimeException e)
        {
            // Just return null, then
        }
        return null;
    }

    // If zone is already normalised, original will be returned
    private static ZonedDateTime normalizeZoneId(ZonedDateTime from)
    {
        ZoneId z = from.getZone();
        ZoneId n = z.normalized();
        if (z.equals(n))
            return from;
        else
            return ZonedDateTime.of(from.toLocalDateTime(), n);
    }

    @SuppressWarnings("valuetype")
    public static @Value Record value(Record record)
    {
        return record;
    }

    @SuppressWarnings("valuetype")
    public static Utility.@Value ListEx value(Utility.@UnknownIfValue ListEx list)
    {
        return list;
    }

    @SuppressWarnings("valuetype")
    public static Utility.@Value ListEx value(@UnknownIfValue List<@Value Object> list)
    {
        return new ListExList(list);
    }

    @SuppressWarnings("valuetype")
    public static Utility.@ImmediateValue ListEx valueImmediate(@UnknownIfValue List<@ImmediateValue Object> list)
    {
        return new ListExList(list);
    }

    public static String _test_valueToString(@Value Object item)
    {
        if (item instanceof Object[])
        {
            @Value Object[] tuple = (@Value Object[])item;
            return "(" + Arrays.stream(tuple).map(DataTypeUtility::_test_valueToString).collect(Collectors.joining(", ")) + ")";
        }
        else if (item instanceof String)
        {
            return "\"" + GrammarUtility.escapeChars((String)item) + "\"";
        }
        else if (item instanceof Number)
        {
            return Utility.numberToString((Number)item);
        }

        return item.toString();
    }

    @OnThread(Tag.Simulation)
    public static String valueToString(@Value Object item) throws UserException, InternalException
    {
        return valueToString(item, null, false, null);
    }

    @OnThread(Tag.Any)
    public static String valueToStringFX(@ImmediateValue Object item) throws UserException, InternalException
    {
        return Utility.launderSimulationEx(() -> valueToString(item));
    }
    
    public static interface Truncater
    {
        public String truncateNumber(@ImmediateValue Number number) throws InternalException, UserException;
        // TODO also allow list truncation
    }

    // If asExpressionOfType != null, convert to Expression, else convert just to value
    @OnThread(Tag.Simulation)
    public static String valueToString(@Value Object item, @Nullable DataType asExpressionOfType, boolean surroundedByBrackets, @Nullable Truncater truncater) throws UserException, InternalException
    {
        if (item instanceof Number)
        {
            return numberToString(item, asExpressionOfType, truncater);
        }
        else if (item instanceof String)
        {
            return "\"" + GrammarUtility.escapeChars(item.toString()) + "\"";
        }
        else if (item instanceof Boolean)
        {
            return item.toString();
        }
        else if (item instanceof TemporalAccessor)
        {
            @Value TemporalAccessor t = Utility.cast(item, TemporalAccessor.class);
            return temporalToString(t, asExpressionOfType);
        }
        else if (item instanceof TaggedValue)
        {
            @Value TaggedValue tv = Utility.cast(item, TaggedValue.class);
            Pair<TypeId, ImmutableList<TagType<DataType>>> asExpressionOfTaggedType = asExpressionOfType == null ? null : asExpressionOfType.apply(new SpecificDataTypeVisitor<Pair<TypeId, ImmutableList<TagType<DataType>>>>() {
                @Override
                public Pair<TypeId, ImmutableList<TagType<DataType>>> tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException
                {
                    return new Pair<>(typeName, tags);
                }
            });
            String tagName = (asExpressionOfTaggedType != null ? ("tag\\\\" + asExpressionOfTaggedType.getFirst().getRaw() + "\\") : "") + tv.getTagName();
            @Nullable @Value Object tvInner = tv.getInner();
            if (tvInner != null)
            {
                @Nullable DataType typeInner = asExpressionOfTaggedType == null ? null : Utility.getI(asExpressionOfTaggedType.getSecond(), tv.getTagIndex()).getInner();
                if (asExpressionOfTaggedType != null)
                    return "@call " + tagName + "(" + valueToString(tvInner, typeInner, true, truncater) + ")";
                else
                    return tagName + "(" + valueToString(tvInner, null, true, truncater) + ")";
            }
            else
            {
                return tagName;
            }
        }
        else if (item instanceof Record)
        {
            @Value Record record = Utility.cast(item, Record.class);
            StringBuilder s = new StringBuilder();
            if (!surroundedByBrackets)
                s.append("(");
            boolean first = true;

            ImmutableMap<@ExpressionIdentifier String, DataType> fieldTypes = asExpressionOfType == null ? null : asExpressionOfType.apply(new SpecificDataTypeVisitor<ImmutableMap<@ExpressionIdentifier String, DataType>>() {
                @Override
                public ImmutableMap<@ExpressionIdentifier String, DataType> record(ImmutableMap<@ExpressionIdentifier String, DataType> fields) throws InternalException, InternalException
                {
                    return fields;
                }
            });
            
            for (Entry<@ExpressionIdentifier String, @Value Object> entry : Utility.iterableStream(record.getFullContent().entrySet().stream().sorted(Comparator.<Entry<@ExpressionIdentifier String, @Value Object>, @ExpressionIdentifier String>comparing(e -> e.getKey()))))
            {
                if (!first)
                    s.append(", ");
                first = false;
                s.append(entry.getKey()).append(": ");
                s.append(valueToString(entry.getValue(), fieldTypes == null ? null : Utility.get(fieldTypes, entry.getKey()), false, truncater));
            }
            if (!surroundedByBrackets)
                s.append(")");
            return s.toString();
        }
        else if (item instanceof ListEx)
        {
            StringBuilder s = new StringBuilder("[");
            ListEx listEx = Utility.cast(item, ListEx.class);
            for (int i = 0; i < listEx.size(); i++)
            {
                if (i != 0)
                    s.append(", ");
                DataType innerType = asExpressionOfType == null ? null : asExpressionOfType.apply(new SpecificDataTypeVisitor<DataType>() {
                    @Override
                    public DataType array(DataType inner) throws InternalException
                    {
                        return inner;
                    }
                });
                s.append(valueToString(listEx.get(i), innerType, false, truncater));
            }
            return s.append("]").toString();
        }
        else if (item instanceof ValueFunctionBase)
        {
            return "<function>";
        }
        else
        {
            throw new InternalException("Unknown internal type: " + item.getClass());
        }
    }

    public static String numberToString(@Value Object item, @Nullable DataType asExpressionOfType, @Nullable Truncater truncater) throws InternalException, UserException
    {
        String number;
        if (truncater != null)
        {
            @SuppressWarnings("valuetype") // Number is always ImmediateValue
            @ImmediateValue Number cast = Utility.cast(item, Number.class);
            number = truncater.truncateNumber(cast);
        }
        else if (item instanceof BigDecimal)
        {
            if (Utility.isIntegral(item))
            {
                number = ((BigDecimal) item).toBigInteger().toString();
            }
            else
                number = ((BigDecimal) item).toPlainString();
        }
        else
            number = item.toString();
        Unit asExpressionOfUnit = asExpressionOfType == null ? null : asExpressionOfType.apply(new SpecificDataTypeVisitor<Unit>() {
            @Override
            public Unit number(NumberInfo displayInfo) throws InternalException
            {
                return displayInfo.getUnit();
            }
        });
        return number + (asExpressionOfUnit != null && !asExpressionOfUnit.equals(Unit.SCALAR) ? "{" + asExpressionOfUnit.toString() + "}" : "");
    }

    public static String temporalToString(@Value TemporalAccessor t, @Nullable DataType asExpressionOfType) throws InternalException
    {
        DateTimeType type;
        if (t instanceof LocalDate)
        {
            type = DateTimeType.YEARMONTHDAY;
        }
        else if (t instanceof YearMonth)
        {
            type = DateTimeType.YEARMONTH;
        }
        else if (t instanceof LocalTime)
        {
            type = DateTimeType.TIMEOFDAY;
        }
        else if (t instanceof LocalDateTime)
        {
            type = DateTimeType.DATETIME;
        }
        else if (t instanceof ZonedDateTime)
        {
            type = DateTimeType.DATETIMEZONED;
        }
        else
        {
            throw new InternalException("Unknown internal temporal type: " + t.getClass());
        }
        DateTimeInfo dateTimeInfo = new DateTimeInfo(type);
        String s = dateTimeInfo.getStrictFormatter().format(t);
        if (asExpressionOfType != null)
            return dateTimeInfo.getType().literalPrefix() + "{" + s + "}";
        else
            return s;
    }

    public static <DT extends DataType> @ImmediateValue TaggedValue makeDefaultTaggedValue(ImmutableList<TagType<DT>> tagTypes) throws InternalException
    {
        OptionalInt noInnerIndex = Utility.findFirstIndex(tagTypes, tt -> tt.getInner() == null);
        if (noInnerIndex.isPresent())
        {
            return TaggedValue.immediate(noInnerIndex.getAsInt(), null, DataTypeUtility.fromTags(tagTypes));
        }
        else
        {
            @Nullable DataType inner = tagTypes.get(0).getInner();
            if (inner == null)
                throw new InternalException("Impossible: no tags without inner value, yet no inner value!");
            return TaggedValue.immediate(0, makeDefaultValue(inner), DataTypeUtility.fromTags(tagTypes));
        }
    }

    public static @ImmediateValue Object makeDefaultValue(DataType dataType) throws InternalException
    {
        return dataType.apply(new DataTypeVisitorEx<@ImmediateValue Object, InternalException>()
        {
            @Override
            public @ImmediateValue Object number(NumberInfo numberInfo) throws InternalException, InternalException
            {
                return DataTypeUtility.value(0);
            }

            @Override
            public @ImmediateValue Object text() throws InternalException, InternalException
            {
                return DataTypeUtility.value("");
            }

            @Override
            public @ImmediateValue Object date(DateTimeInfo dateTimeInfo) throws InternalException, InternalException
            {
                return dateTimeInfo.getDefaultValue();
            }

            @Override
            public @ImmediateValue Object bool() throws InternalException, InternalException
            {
                return DataTypeUtility.value(false);
            }

            @Override
            public @ImmediateValue Object tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, InternalException
            {
                return makeDefaultTaggedValue(tags);
            }

            @Override
            public @ImmediateValue Object record(ImmutableMap<@ExpressionIdentifier String, DataType> fields) throws InternalException, InternalException
            {
                ImmutableMap.Builder<@ExpressionIdentifier String, @ImmediateValue Object> values = ImmutableMap.builderWithExpectedSize(fields.size());
                for (Entry<@ExpressionIdentifier String, DataType> entry : fields.entrySet())
                {
                    values.put(entry.getKey(), makeDefaultValue(entry.getValue()));
                }
                
                return RecordMap.immediate(values.build());
            }

            @Override
            public @ImmediateValue Object array(@Nullable DataType inner) throws InternalException, InternalException
            {
                return DataTypeUtility.<@Value Object>valueImmediate(Collections.<@ImmediateValue Object>emptyList());
            }
        });
    }

    // Fetches a ListEx from the simulation thread and returns it as a flat list on the FX thread.
    @OnThread(Tag.FXPlatform)
    public static List<@Value Object> fetchList(ListEx simList) throws InternalException
    {
        CompletableFuture<Either<Exception, List<@Value Object>>> f = new CompletableFuture<>();
        Workers.onWorkerThread("Fetch list", Priority.FETCH, () -> {
            try
            {
                ArrayList<@Value Object> r = new ArrayList<>(simList.size());
                for (int i = 0; i < simList.size(); i++)
                {
                    r.add(simList.get(i));
                }
                f.complete(Either.right(r));
            }
            catch (InternalException | UserException e)
            {
                f.complete(Either.left(e));
            }
        });
        Either<Exception, List<@Value Object>> either;
        try
        {
            either = f.get();
        }
        catch (InterruptedException | ExecutionException e)
        {
            throw new InternalException("Error fetching list", e);
        }
        if (either.isLeft())
            throw new InternalException("Error fetching list", either.getLeft("Impossible"));
        else
            return either.getRight("Error fetching list");
    }
    
    public static class PositionedUserException extends UserException
    {
        private final int position;

        public PositionedUserException(String message, int position)
        {
            super(message);
            this.position = position;
        }

        public int getPosition()
        {
            return position;
        }
    }

    @OnThread(Tag.Any)
    public static @ImmediateValue TemporalAccessor parseTemporalFlexible(DateTimeInfo dateTimeInfo, StringView src) throws PositionedUserException
    {
        src.skipSpaces();
        ImmutableList<DateTimeFormatter> formatters = dateTimeInfo.getFlexibleFormatters().stream().flatMap(ImmutableList::stream).collect(ImmutableList.<DateTimeFormatter>toImmutableList());
        // Updated char position and return value:
        ArrayList<Pair<Integer, @Value TemporalAccessor>> possibles = new ArrayList<>();
        WrappedCharSequence wrapped = Utility.wrapPreprocessDate(src.parseProgress.src, src.parseProgress.curCharIndex);
        ArrayList<DateTimeFormatter> possibleFormatters = new ArrayList<>();
        for (DateTimeFormatter formatter : formatters)
        {
            try
            {
                ParsePosition position = new ParsePosition(src.parseProgress.curCharIndex);
                TemporalAccessor temporalAccessor = formatter.parse(wrapped, position);
                @Value TemporalAccessor value = value(dateTimeInfo, temporalAccessor);
                if (value != null)
                {
                    possibles.add(new Pair<>(wrapped.translateWrappedToOriginalPos(position.getIndex()), value));
                    possibleFormatters.add(formatter);
                }
            }
            catch (DateTimeParseException e)
            {
                // Try next one
            }
        }
        if (possibles.size() == 1)
        {
            src.setPosition(possibles.get(0).getFirst());
            @Nullable @ImmediateValue TemporalAccessor value = value(dateTimeInfo, possibles.get(0).getSecond());
            if (value != null)
                return value;
        }
        else if (possibles.size() > 1)
        {
            ArrayList<Pair<Integer, TemporalAccessor>> possiblesByLength = new ArrayList<>(possibles);
            Collections.sort(possiblesByLength, Pair.<Integer, TemporalAccessor>comparatorFirst());
            // Choose the longest one, if it's strictly longer than the others:
            int longest = possiblesByLength.get(possiblesByLength.size() - 1).getFirst();
            if (longest > possiblesByLength.get(possiblesByLength.size() - 2).getFirst())
            {
                Pair<Integer, TemporalAccessor> chosen = possiblesByLength.get(possiblesByLength.size() - 1);
                src.setPosition(chosen.getFirst());
                @ImmediateValue TemporalAccessor value = value(dateTimeInfo, chosen.getSecond());
                if (value != null)
                    return value;
            }
            // If all the values of longest length are the same, that's fine:
            @SuppressWarnings("type.argument")
            List<Pair<Integer, TemporalAccessor>> distinctValues =
                StreamEx.of(possibles.stream())
                    .filter(p -> p.getFirst() == longest)
                    // Time zones are annoying because UTC can parse as UTC or Etc/UTC but they are both the same when
                    // normalized so we normalize while checking for distinct values:
                    .distinct(p -> p.mapSecond(t -> t instanceof ZonedDateTime ? normalizeZoneId((ZonedDateTime) t) : t))
                    .collect(Collectors.<Pair<Integer, TemporalAccessor>>toList());
            if (distinctValues.size() == 1)
            {
                Pair<Integer, TemporalAccessor> chosen = distinctValues.get(0);
                src.setPosition(chosen.getFirst());
                @ImmediateValue TemporalAccessor value = value(dateTimeInfo, chosen.getSecond());
                if (value != null)
                    return value;
            }
            
            // Otherwise, throw because it's too ambiguous:
            throw new PositionedUserException(Integer.toString(distinctValues.size()) + " ways to interpret " + dateTimeInfo + " value "
                + src.snippet() + ": "
                + Utility.listToString(Utility.<Pair<Integer, @Value TemporalAccessor>, @Value TemporalAccessor>mapList(possibles, p -> p.getSecond()))
                + " using formatters "
                + Utility.listToString(possibleFormatters), src.getPosition());
        }

        //Log.debug("Wrapped: " + wrapped.toString() + " matches: " + possibles.size());
        throw new PositionedUserException("Expected " + DataType.date(dateTimeInfo).toString() + " value but found: " + src.snippet(), src.getPosition());
    }

    @OnThread(Tag.Simulation)
    public static Comparator<@Value Object> getValueComparator()
    {
        return (Comparator<@Value Object>)(@Value Object a, @Value Object b) -> {
            try
            {
                return Utility.compareValues(a, b);
            }
            catch (InternalException | UserException e)
            {
                throw new RuntimeException(e);
            }
        };
    }

    public static boolean isNumber(DataType type)
    {
        try
        {
            return type.apply(new FlatDataTypeVisitor<Boolean>(false)
            {
                @Override
                public Boolean number(NumberInfo numberInfo) throws InternalException, InternalException
                {
                    return true;
                }
            });
        }
        catch (InternalException e)
        {
            Log.log(e);
            return false;
        }
    }
    
    public static TypeId getTaggedTypeName(DataType type) throws InternalException
    {
        return type.apply(new SpecificDataTypeVisitor<TypeId>() {
            @Override
            public TypeId tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException
            {
                return typeName;
            }
        });
    }

    public static ImmutableList<TagType<DataType>> getTagTypes(DataType type) throws InternalException
    {
        return type.apply(new SpecificDataTypeVisitor<ImmutableList<TagType<DataType>>>() {
            @Override
            public ImmutableList<TagType<DataType>> tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException
            {
                return tags;
            }
        });
    }

    // Keeps track of a trailing substring of a string.  Saves memory compared to copying
    // the substrings over and over.  The data is immutable, the position is mutable.  Wraps ParseProgress
    public static class StringView
    {
        private static final ImmutableList<Integer> whiteSpaceCategories = ImmutableList.of(
            (int)Character.SPACE_SEPARATOR,
            (int)Character.LINE_SEPARATOR,
            (int)Character.PARAGRAPH_SEPARATOR,
            // Not really whitespace but contains \t and \n so we want to skip:
            (int)Character.CONTROL
        );
        
        private ParseProgress parseProgress;
        
        public StringView(String s)
        {
            this.parseProgress = ParseProgress.fromStart(s);
        }
        
        public StringView(ParseProgress parseProgress)
        {
            this.parseProgress = parseProgress;
        }
        
        // Tries to read the given literal, having skipped any spaces at current position.
        // If found, the string is consumed and true is returned. If not found, the spaces
        // are still consumed, and false is returned.
        public boolean tryRead(String literal)
        {
            skipSpaces();
            ParseProgress attempt = parseProgress.consumeNext(literal);
            if (attempt == null)
                return false;
            parseProgress = attempt;
            return true;
        }

        public boolean tryReadIgnoreCase(String literal)
        {
            skipSpaces();
            ParseProgress attempt = parseProgress.consumeNextIC(literal);
            if (attempt == null)
                return false;
            parseProgress = attempt;
            return true;
        }

        public void skipSpaces()
        {
            parseProgress = parseProgress.skipSpaces();
        }

        // TODO use styledstring here
        public String snippet()
        {
            StringBuilder s = new StringBuilder();
            // Add prefix:
            s.append("\"" + parseProgress.src.substring(Math.max(0, parseProgress.curCharIndex - 20), parseProgress.curCharIndex) + ">>>");
            return s.append(parseProgress.src.substring(parseProgress.curCharIndex, Math.min(parseProgress.curCharIndex + 40, parseProgress.src.length())) + "\"").toString();
        }

        // Reads up until that character, and also consumes that character
        // Returns null if end of string is found first
        public @Nullable String readUntil(char c)
        {
            @Nullable Pair<String, ParseProgress> p = parseProgress.consumeUpToAndIncluding("" + c);
            if (p != null)
            {
                parseProgress = p.getSecond();
                return p.getFirst();
            }
            return null;
        }

        // Doesn't skip spaces!
        public String consumeNumbers()
        {
            Pair<String, ParseProgress> p = parseProgress.consumeNumbers();
            parseProgress = p.getSecond();
            return p.getFirst();
        }

        public void setPosition(int pos)
        {
            parseProgress = ParseProgress.fromStart(parseProgress.src).skip(pos);
        }

        public int getPosition()
        {
            return parseProgress.curCharIndex;
        }

        public ParseProgress getParseProgress()
        {
            return parseProgress;
        }
    }

    /**
     * Returns a predicate that checks whether the unit is featured anywhere
     * in the given types (incl transitively)
     */
    public static Predicate<@UnitIdentifier String> featuresUnit(List<DataType> dataTypes)
    {
        // Pre-calculate set of all units contained by the given types:
        HashSet<@UnitIdentifier String> allUnits = new HashSet<>();

        try
        {
            for (DataType dataType : dataTypes)
            {
                dataType.apply(new DataTypeVisitorEx<UnitType, InternalException>()
                {
                    private void addUnit(Unit unit)
                    {
                        allUnits.addAll(unit.getDetails().keySet().stream().<@UnitIdentifier String>map(u -> u.getName()).collect(Collectors.<@UnitIdentifier String>toList()));
                    }

                    @Override
                    public UnitType number(NumberInfo numberInfo) throws InternalException, InternalException
                    {
                        addUnit(numberInfo.getUnit());
                        return UnitType.UNIT;
                    }
                    
                    @Override
                    public UnitType text() throws InternalException, InternalException
                    {
                        return UnitType.UNIT;
                    }

                    @Override
                    public UnitType date(DateTimeInfo dateTimeInfo) throws InternalException, InternalException
                    {
                        return UnitType.UNIT;
                    }

                    @Override
                    public UnitType bool() throws InternalException, InternalException
                    {
                        return UnitType.UNIT;
                    }

                    @Override
                    public UnitType tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, InternalException
                    {
                        for (Either<Unit, DataType> typeVar : typeVars)
                        {
                            typeVar.ifLeft(this::addUnit);
                        }
                        for (TagType<DataType> tag : tags)
                        {
                            if (tag.getInner() != null)
                                tag.getInner().apply(this);
                        }
                        return UnitType.UNIT;
                    }

                    @Override
                    public UnitType record(ImmutableMap<@ExpressionIdentifier String, DataType> fields) throws InternalException, InternalException
                    {
                        for (Entry<String, DataType> entry : fields.entrySet())
                        {
                            entry.getValue().apply(this);
                        }
                        return UnitType.UNIT;
                    }

                    @Override
                    public UnitType array(DataType inner) throws InternalException, InternalException
                    {
                        inner.apply(this);
                        return UnitType.UNIT;
                    }
                });
            }

            return allUnits::contains;
        }
        catch (InternalException e)
        {
            Log.log(e);
            // If in doubt, include all types:
            return u -> true;
        }
    }

    /**
     * Returns a predicate that checks whether the tagged type is featured anywhere
     * in the given types (incl transitively)
     */
    public static Predicate<TypeId> featuresTaggedType(List<DataType> dataTypes)
    {
        // Pre-calculate set of all types contained within the given types:
        HashSet<TypeId> allTypes = new HashSet<>();

        try
        {
            for (DataType dataType : dataTypes)
            {
                dataType.apply(new DataTypeVisitorEx<UnitType, InternalException>()
                {
                    @Override
                    public UnitType number(NumberInfo numberInfo) throws InternalException, InternalException
                    {
                        return UnitType.UNIT;
                    }

                    @Override
                    public UnitType text() throws InternalException, InternalException
                    {
                        return UnitType.UNIT;
                    }

                    @Override
                    public UnitType date(DateTimeInfo dateTimeInfo) throws InternalException, InternalException
                    {
                        return UnitType.UNIT;
                    }

                    @Override
                    public UnitType bool() throws InternalException, InternalException
                    {
                        return UnitType.UNIT;
                    }

                    @Override
                    public UnitType tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, InternalException
                    {
                        allTypes.add(typeName);
                        for (TagType<DataType> tag : tags)
                        {
                            if (tag.getInner() != null)
                                tag.getInner().apply(this);
                        }
                        return UnitType.UNIT;
                    }

                    @Override
                    public UnitType record(ImmutableMap<@ExpressionIdentifier String, DataType> fields) throws InternalException, InternalException
                    {
                        for (Entry<String, DataType> entry : fields.entrySet())
                        {
                            entry.getValue().apply(this);
                        }
                        return UnitType.UNIT;
                    }

                    @Override
                    public UnitType array(DataType inner) throws InternalException, InternalException
                    {
                        inner.apply(this);
                        return UnitType.UNIT;
                    }
                });
            }

            return allTypes::contains;
        }
        catch (InternalException e)
        {
            Log.log(e);
            // If in doubt, include all types:
            return u -> true;
        }
    }

    /**
     * Things like TreeMap say that the Comparable instance should
     * be compatible with .equals, i.e. that if they compareTo equal,
     * then equals should return true.  But we have object arrays
     * which don't implement equals reasonably, and things like numbers
     * should compare equal even when they use different types.
     * 
     * So this wraps an @Value Object just for the purpose of implementing
     * equals and compareTo sensibly, so you can use @Value Object as a key
     * in a TreeMap.
     */
    @OnThread(Tag.Simulation)
    public static class ComparableValue implements Comparable<ComparableValue>
    {
        private final @Value Object value;

        public ComparableValue(@Value Object value)
        {
            this.value = value;
        }

        @Override
        public int hashCode()
        {
            // Accept hash collisions; should be using comparable implementation for maps anyway
            return 1;
        }

        @Override
        public boolean equals(@Nullable Object obj)
        {
            return obj instanceof ComparableValue && compareTo((ComparableValue) obj) == 0;
        }

        @Override
        public int compareTo(ComparableValue o)
        {
            try
            {
                return Utility.compareValues(value, o.value);
            }
            catch (InternalException | UserException e)
            {
                Log.log(e);
                // Don't want to throw, so this is best we can do:
                return -1;
            }
        }

        public @Value Object getValue()
        {
            return value;
        }

        // For debugging:
        @Override
        public String toString()
        {
            return "ComparableValue{" +
                    "value=" + DataTypeUtility._test_valueToString(value) +
                    '}';
        }
    }
    
    public static <T> TaggedTypeDefinitionBase fromTags(ImmutableList<TagType<T>> tags)
    {
        return tagIndex -> {
            if (tagIndex >=0 && tagIndex < tags.size())
                return tags.get(tagIndex).getName();
            return "InvalidTag" + (tagIndex < 0 ? "Neg" : "") + Math.abs(tagIndex);
        };
    }
}
