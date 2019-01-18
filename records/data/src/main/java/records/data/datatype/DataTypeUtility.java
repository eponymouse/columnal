package records.data.datatype;

import annotation.identifier.qual.UnitIdentifier;
import annotation.qual.UnknownIfValue;
import annotation.qual.Value;
import annotation.userindex.qual.UserIndex;
import com.google.common.collect.ImmutableList;
import log.Log;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.ArrayColumnStorage;
import records.data.BooleanColumnStorage;
import records.data.Column.ProgressListener;
import records.data.ColumnStorage;
import records.data.ColumnStorage.BeforeGet;
import records.data.NumericColumnStorage;
import records.data.StringColumnStorage;
import records.data.TaggedColumnStorage;
import records.data.TemporalColumnStorage;
import records.data.TupleColumnStorage;
import records.data.datatype.DataType.DataTypeVisitor;
import records.data.datatype.DataType.DataTypeVisitorEx;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.TagType;
import records.data.datatype.DataTypeValue.GetValue;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.GrammarUtility;
import records.loadsave.OutputBuilder;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Pair;
import utility.TaggedValue;
import utility.UnitType;
import utility.Utility;
import utility.Utility.ListEx;
import utility.Utility.ListExList;
import utility.Utility.WrappedCharSequence;
import utility.ValueFunction;
import utility.Workers;
import utility.Workers.Priority;

import java.math.BigDecimal;
import java.text.ParsePosition;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetTime;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
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

    @OnThread(Tag.Simulation)
    @SuppressWarnings("unchecked")
    public static ColumnStorage<?> makeColumnStorage(final DataType inner, ColumnStorage.@Nullable BeforeGet<?> beforeGet) throws InternalException
    {
        return inner.apply(new DataTypeVisitorEx<ColumnStorage<?>, InternalException>()
        {
            @Override
            @OnThread(Tag.Simulation)
            public ColumnStorage<?> number(NumberInfo displayInfo) throws InternalException
            {
                return new NumericColumnStorage(displayInfo, (BeforeGet<NumericColumnStorage>)beforeGet);
            }

            @Override
            @OnThread(Tag.Simulation)
            public ColumnStorage<?> bool() throws InternalException
            {
                return new BooleanColumnStorage((BeforeGet<BooleanColumnStorage>)beforeGet);
            }

            @Override
            @OnThread(Tag.Simulation)
            public ColumnStorage<?> text() throws InternalException
            {
                return new StringColumnStorage((BeforeGet<StringColumnStorage>)beforeGet);
            }

            @Override
            @OnThread(Tag.Simulation)
            public ColumnStorage<?> date(DateTimeInfo dateTimeInfo) throws InternalException
            {
                return new TemporalColumnStorage(dateTimeInfo, (BeforeGet<TemporalColumnStorage>)beforeGet);
            }

            @Override
            @OnThread(Tag.Simulation)
            public ColumnStorage<?> tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException
            {
                return new TaggedColumnStorage(typeName, typeVars, tags, (BeforeGet<TaggedColumnStorage>)beforeGet);
            }

            @Override
            @OnThread(Tag.Simulation)
            public ColumnStorage<?> tuple(ImmutableList<DataType> innerTypes) throws InternalException
            {
                return new TupleColumnStorage(innerTypes, beforeGet);
            }

            @Override
            @OnThread(Tag.Simulation)
            public ColumnStorage<?> array(@Nullable DataType inner) throws InternalException
            {
                return new ArrayColumnStorage(inner, (BeforeGet<ArrayColumnStorage>)beforeGet);
            }
        });
    }

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

    @SuppressWarnings("valuetype")
    public static <T extends Number> @Value T value(@UnknownIfValue T number)
    {
        return number;
    }

    @SuppressWarnings("valuetype")
    public static @Value Boolean value(@UnknownIfValue Boolean bool)
    {
        return bool;
    }

    @SuppressWarnings("valuetype")
    public static @Value String value(@UnknownIfValue String string)
    {
        return string;
    }

    @SuppressWarnings("valuetype")
    public static @Value TemporalAccessor value(DateTimeInfo dest, @UnknownIfValue TemporalAccessor t)
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
                else
                    return LocalDateTime.from(t);
            case DATETIMEZONED:
                if (t instanceof ZonedDateTime)
                    return t;
                else
                    return ZonedDateTime.from(t);
        }
        return t;
    }

    @SuppressWarnings("valuetype")
    public static @Value Object @Value [] value(@Value Object [] tuple)
    {
        return tuple;
    }

    @SuppressWarnings("valuetype")
    public static Utility.@Value ListEx value(Utility.@UnknownIfValue ListEx list)
    {
        return list;
    }

    @SuppressWarnings("valuetype")
    public static @Value ValueFunction value(@UnknownIfValue ValueFunction function)
    {
        return function;
    }

    @SuppressWarnings("valuetype")
    public static Utility.@Value ListEx value(@UnknownIfValue List<@Value ? extends Object> list)
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

        return item.toString();
    }

    @OnThread(Tag.Simulation)
    public static String valueToString(DataType dataType, @Value Object item, @Nullable DataType parent) throws UserException, InternalException
    {
        return valueToString(dataType, item, parent, false);
    }

    @OnThread(Tag.Simulation)
    public static String valueToString(DataType dataType, @Value Object item, @Nullable DataType parent, boolean asExpression) throws UserException, InternalException
    {
        return dataType.apply(new DataTypeVisitor<String>()
        {
            @Override
            public String number(NumberInfo numberInfo) throws InternalException, UserException
            {
                String number;
                if (item instanceof BigDecimal)
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
                return number + (asExpression && !numberInfo.getUnit().equals(Unit.SCALAR) ? "{" + numberInfo.getUnit().toString() + "}" : "");
            }

            @Override
            public String text() throws InternalException, UserException
            {
                return "\"" + GrammarUtility.escapeChars(item.toString()) + "\"";
            }

            @Override
            public String date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
            {
                String s = dateTimeInfo.getStrictFormatter().format((TemporalAccessor) item);
                if (asExpression)
                    return dateTimeInfo.getType().literalPrefix() + "{" + s + "}";
                else
                    return s;
            }

            @Override
            public String bool() throws InternalException, UserException
            {
                return item.toString();
            }

            @Override
            @OnThread(Tag.Simulation)
            public String tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, UserException
            {
                TaggedValue tv = (TaggedValue)item;
                String tagName = (asExpression ? ("@tag " + typeName.getRaw() + ":") : "") + tags.get(tv.getTagIndex()).getName();
                @Nullable @Value Object tvInner = tv.getInner();
                if (tvInner != null)
                {
                    @Nullable DataType typeInner = tags.get(tv.getTagIndex()).getInner();
                    if (typeInner == null)
                        throw new InternalException("Tag value inner but missing type inner: " + typeName + " " + tagName);
                    if (asExpression)
                        return "@call " + tagName + "(" + valueToString(typeInner, tvInner, dataType, asExpression) + ")";
                    else
                        return tagName + "(" + valueToString(typeInner, tvInner, dataType, asExpression) + ")";
                }
                else
                {
                    return tagName;
                }
            }

            @Override
            @OnThread(Tag.Simulation)
            public String tuple(ImmutableList<DataType> inner) throws InternalException, UserException
            {
                @Value Object[] tuple = (@Value Object[])item;
                StringBuilder s = new StringBuilder();
                if (parent == null || !parent.isTagged())
                    s.append("(");
                for (int i = 0; i < tuple.length; i++)
                {
                    if (i != 0)
                        s.append(",");
                    s.append(valueToString(inner.get(i), tuple[i], dataType, asExpression));
                }
                if (parent == null || !parent.isTagged())
                    s.append(")");
                return s.toString();
            }

            @Override
            @OnThread(Tag.Simulation)
            public String array(@Nullable DataType inner) throws InternalException, UserException
            {
                StringBuilder s = new StringBuilder("[");
                ListEx listEx = (ListEx)item;
                for (int i = 0; i < listEx.size(); i++)
                {
                    if (i != 0)
                        s.append(",");
                    if (inner == null)
                        throw new InternalException("Array has empty type but is not empty");
                    s.append(valueToString(inner, listEx.get(i), dataType, asExpression));
                }
                return s.append("]").toString();
            }

            @Override
            public String function(DataType argType, DataType resultType) throws InternalException, UserException
            {
                return "<function>";
            }
        });
    }

    public static DataTypeValue listToType(DataType elementType, ListEx listEx) throws InternalException, UserException
    {
        return elementType.fromCollapsed((i, prog) -> listEx.get(i));
    }

    public static GetValue<TaggedValue> toTagged(GetValue<Integer> g, ImmutableList<TagType<DataTypeValue>> tagTypes)
    {
        return new GetValue<TaggedValue>()
        {
            @Override
            public TaggedValue getWithProgress(int index, @Nullable ProgressListener progressListener) throws UserException, InternalException
            {
                int tagIndex = g.getWithProgress(index, progressListener);
                @Nullable DataTypeValue inner = tagTypes.get(tagIndex).getInner();
                if (inner == null)
                {
                    return new TaggedValue(tagIndex, null);
                }
                else
                {
                    @Value Object innerVal = inner.getCollapsed(index);
                    return new TaggedValue(tagIndex, innerVal);
                }
            }

            @Override
            public @OnThread(Tag.Simulation) void set(int index, Either<String, TaggedValue> errOrValue) throws InternalException, UserException
            {
                g.set(index, errOrValue.map(v -> v.getTagIndex()));
                errOrValue.eitherEx_(err -> {}, value -> {
                    @Nullable DataTypeValue inner = tagTypes.get(value.getTagIndex()).getInner();
                    if (inner != null)
                    {
                        @Nullable @Value Object innerVal = value.getInner();
                        if (innerVal == null)
                            throw new InternalException("Inner type present but not inner value " + tagTypes + " #" + value.getTagIndex());
                        inner.setCollapsed(index, Either.right(innerVal));
                    }
                });
            }
        };
    }

    public static <DT extends DataType> TaggedValue makeDefaultTaggedValue(ImmutableList<TagType<DT>> tagTypes) throws InternalException
    {
        OptionalInt noInnerIndex = Utility.findFirstIndex(tagTypes, tt -> tt.getInner() == null);
        if (noInnerIndex.isPresent())
        {
            return new TaggedValue(noInnerIndex.getAsInt(), null);
        }
        else
        {
            @Nullable DataType inner = tagTypes.get(0).getInner();
            if (inner == null)
                throw new InternalException("Impossible: no tags without inner value, yet no inner value!");
            return new TaggedValue(0, makeDefaultValue(inner));
        }
    }

    public static @Value Object makeDefaultValue(DataType dataType) throws InternalException
    {
        return dataType.apply(new DataTypeVisitorEx<@Value Object, InternalException>()
        {
            @Override
            public @Value Object number(NumberInfo numberInfo) throws InternalException, InternalException
            {
                return DataTypeUtility.value(0);
            }

            @Override
            public @Value Object text() throws InternalException, InternalException
            {
                return DataTypeUtility.value("");
            }

            @Override
            public @Value Object date(DateTimeInfo dateTimeInfo) throws InternalException, InternalException
            {
                return dateTimeInfo.getDefaultValue();
            }

            @Override
            public @Value Object bool() throws InternalException, InternalException
            {
                return DataTypeUtility.value(false);
            }

            @Override
            public @Value Object tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, InternalException
            {
                return makeDefaultTaggedValue(tags);
            }

            @Override
            public @Value Object tuple(ImmutableList<DataType> inner) throws InternalException, InternalException
            {
                @Value Object @Value[] tuple = DataTypeUtility.value(new Object[inner.size()]);
                for (int i = 0; i < inner.size(); i++)
                {
                    tuple[i] = makeDefaultValue(inner.get(i));
                }
                return tuple;
            }

            @Override
            public @Value Object array(@Nullable DataType inner) throws InternalException, InternalException
            {
                return DataTypeUtility.value(Collections.emptyList());
            }
        });
    }

    public static GetValue<@Value ListEx> toListEx(DataType innerType, GetValue<Pair<Integer, DataTypeValue>> g)
    {
        return new GetValue<@Value ListEx>()
        {
            @Override
            public @Value ListEx getWithProgress(int index, @Nullable ProgressListener progressListener) throws UserException, InternalException
            {
                Pair<Integer, DataTypeValue> p = g.getWithProgress(index, progressListener);
                return DataTypeUtility.value(new ListEx()
                {
                    @Override
                    public int size() throws InternalException, UserException
                    {
                        return p.getFirst();
                    }

                    @Override
                    public @Value Object get(int index) throws InternalException, UserException
                    {
                        return p.getSecond().getCollapsed(index);
                    }
                });
            }

            @Override
            public @OnThread(Tag.Simulation) void set(int index, Either<String,ListEx> value) throws InternalException, UserException
            {
                g.set(index, value.mapEx(v -> new Pair<>(v.size(), innerType.fromCollapsed((i, prog) -> v.get(i)))));
            }
        };
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

    @OnThread(Tag.Any)
    public static @Value TemporalAccessor parseTemporalFlexible(DateTimeInfo dateTimeInfo, StringView src) throws UserException
    {
        src.skipSpaces();
        ImmutableList<DateTimeFormatter> formatters = dateTimeInfo.getFlexibleFormatters().stream().flatMap(ImmutableList::stream).collect(ImmutableList.<DateTimeFormatter>toImmutableList());
        // Updated char position and return value:
        ArrayList<Pair<Integer, @Value TemporalAccessor>> possibles = new ArrayList<>();
        WrappedCharSequence wrapped = Utility.wrapPreprocessDate(src.original, src.charStart);
        ArrayList<DateTimeFormatter> possibleFormatters = new ArrayList<>();
        for (DateTimeFormatter formatter : formatters)
        {
            try
            {
                ParsePosition position = new ParsePosition(src.charStart);
                TemporalAccessor temporalAccessor = formatter.parse(wrapped, position);
                possibles.add(new Pair<>(wrapped.translateWrappedToOriginalPos(position.getIndex()), value(dateTimeInfo, temporalAccessor)));
                possibleFormatters.add(formatter);
            }
            catch (DateTimeParseException e)
            {
                // Try next one
            }
        }
        if (possibles.size() == 1)
        {
            src.charStart = possibles.get(0).getFirst();
            return value(dateTimeInfo, possibles.get(0).getSecond());
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
                src.charStart = chosen.getFirst();
                return value(dateTimeInfo, chosen.getSecond());
            }
            // If all the values of longest length are the same, that's fine:
            HashSet<Pair<Integer, TemporalAccessor>> distinctValues = new HashSet<>(
                possibles.stream().filter(p -> p.getFirst() == longest).collect(Collectors.<Pair<Integer, TemporalAccessor>>toList())
            );
            if (distinctValues.size() == 1)
            {
                Pair<Integer, TemporalAccessor> chosen = distinctValues.iterator().next();
                src.charStart = chosen.getFirst();
                return value(dateTimeInfo, chosen.getSecond());
            }
            
            // Otherwise, throw because it's too ambiguous:
            throw new UserException(Integer.toString(distinctValues.size()) + " ways to interpret " + dateTimeInfo + " value "
                + src.snippet() + ": "
                + Utility.listToString(Utility.<Pair<Integer, @Value TemporalAccessor>, @Value TemporalAccessor>mapList(possibles, p -> p.getSecond()))
                + " using formatters "
                + Utility.listToString(possibleFormatters));
        }

        //Log.debug("Wrapped: " + wrapped.toString() + " matches: " + possibles.size());
        throw new UserException("Expected " + dateTimeInfo + " value but found: " + src.snippet());
    }

    // Keeps track of a trailing substring of a string.  Saves memory compared to copying
    // the substrings over and over.  The data is immutable, the position is mutable.
    public static class StringView
    {
        public final String original;
        public int charStart;
        
        public StringView(String s)
        {
            this.original = s;
            this.charStart = 0;
        }
        
        public StringView(StringView stringView)
        {
            this.original = stringView.original;
            this.charStart = stringView.charStart;
        }
        
        // Tries to read the given literal, having skipped any spaces at current position.
        // If found, the string is consumed and true is returned. If not found, the spaces
        // are still consumed, and false is returned.
        public boolean tryRead(String literal)
        {
            skipSpaces();
            if (original.regionMatches(charStart, literal, 0, literal.length()))
            {
                charStart += literal.length();
                return true;
            }
            return false;
        }

        public boolean tryReadIgnoreCase(String literal)
        {
            skipSpaces();
            if (original.regionMatches(true, charStart, literal, 0, literal.length()))
            {
                charStart += literal.length();
                return true;
            }
            return false;
        }

        public void skipSpaces()
        {
            // Don't try and get clever recurse to call tryRead, because it calls us!
            while (charStart < original.length() && original.charAt(charStart) == ' ')
                charStart += 1;
        }

        // TODO use styledstring here
        public String snippet()
        {
            StringBuilder s = new StringBuilder();
            // Add prefix:
            s.append("\"" + original.substring(Math.max(0, charStart - 20), charStart) + ">>>");
            return s.append(original.substring(charStart, Math.min(charStart + 40, original.length())) + "\"").toString();
        }

        // Reads up until that character, and also consumes that character
        // Returns null if end of string is found first
        public @Nullable String readUntil(char c)
        {
            int start = charStart;
            while (charStart < original.length() && original.charAt(charStart) != c)
            {
                charStart += 1;
            }
            if (charStart >= original.length())
                return null;
            // End is exclusive, but then add one to consume it:
            return original.substring(start, charStart++);
        }

        // Doesn't skip spaces!
        public String consumeNumbers()
        {
            int start = charStart;
            while (charStart < original.length() && Character.isDigit(original.charAt(charStart)))
                charStart += 1;
            return original.substring(start, charStart);
        }
    }

    /**
     * Returns a predicate that checks whether the unit is featured anywhere
     * in the given types (incl transitively)
     */
    public static Predicate<@UnitIdentifier String> featuresUnit(List<DataType> dataTypes)
    {
        // Pre-calculate set of all units:
        HashSet<@UnitIdentifier String> allUnits = new HashSet<>();

        try
        {
            for (DataType dataType : dataTypes)
            {
                dataType.apply(new DataTypeVisitorEx<UnitType, InternalException>()
                {
                    private void addUnit(Unit unit)
                    {
                        allUnits.addAll(unit.getDetails().keySet().stream().map(u -> u.getName()).collect(Collectors.<@UnitIdentifier String>toList()));
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
                    public UnitType tuple(ImmutableList<DataType> inner) throws InternalException, InternalException
                    {
                        for (DataType t : inner)
                        {
                            t.apply(this);
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
        // Pre-calculate set of all types:
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
                    public UnitType tuple(ImmutableList<DataType> inner) throws InternalException, InternalException
                    {
                        for (DataType t : inner)
                        {
                            t.apply(this);
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

}
