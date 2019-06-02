package records.data.datatype;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.identifier.qual.UnitIdentifier;
import annotation.qual.UnknownIfValue;
import annotation.qual.Value;
import annotation.userindex.qual.UserIndex;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import log.Log;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.ArrayColumnStorage;
import records.data.BooleanColumnStorage;
import records.data.ColumnStorage;
import records.data.ColumnStorage.BeforeGet;
import records.data.NumericColumnStorage;
import records.data.StringColumnStorage;
import records.data.TaggedColumnStorage;
import records.data.TemporalColumnStorage;
import records.data.RecordColumnStorage;
import records.data.datatype.DataType.DataTypeVisitor;
import records.data.datatype.DataType.DataTypeVisitorEx;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.FlatDataTypeVisitor;
import records.data.datatype.DataType.SpecificDataTypeVisitor;
import records.data.datatype.DataType.TagType;
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
import utility.Utility.Record;
import utility.Utility.RecordMap;
import utility.Utility.WrappedCharSequence;
import utility.Workers;
import utility.Workers.Priority;

import java.math.BigDecimal;
import java.text.ParsePosition;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
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

    @OnThread(Tag.Simulation)
    @SuppressWarnings("unchecked")
    public static ColumnStorage<?> makeColumnStorage(final DataType inner, ColumnStorage.@Nullable BeforeGet<?> beforeGet, boolean isImmediateData) throws InternalException
    {
        return inner.apply(new DataTypeVisitorEx<ColumnStorage<?>, InternalException>()
        {
            @Override
            @OnThread(Tag.Simulation)
            public ColumnStorage<?> number(NumberInfo displayInfo) throws InternalException
            {
                return new NumericColumnStorage(displayInfo, (BeforeGet<NumericColumnStorage>)beforeGet, isImmediateData);
            }

            @Override
            @OnThread(Tag.Simulation)
            public ColumnStorage<?> bool() throws InternalException
            {
                return new BooleanColumnStorage((BeforeGet<BooleanColumnStorage>)beforeGet, isImmediateData);
            }

            @Override
            @OnThread(Tag.Simulation)
            public ColumnStorage<?> text() throws InternalException
            {
                return new StringColumnStorage((BeforeGet<StringColumnStorage>)beforeGet, isImmediateData);
            }

            @Override
            @OnThread(Tag.Simulation)
            public ColumnStorage<?> date(DateTimeInfo dateTimeInfo) throws InternalException
            {
                return new TemporalColumnStorage(dateTimeInfo, (BeforeGet<TemporalColumnStorage>)beforeGet, isImmediateData);
            }

            @Override
            @OnThread(Tag.Simulation)
            public ColumnStorage<?> tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException
            {
                return new TaggedColumnStorage(typeName, typeVars, tags, (BeforeGet<TaggedColumnStorage>)beforeGet, isImmediateData);
            }

            @Override
            @OnThread(Tag.Simulation)
            public ColumnStorage<?> record(ImmutableMap<@ExpressionIdentifier String, DataType> fields) throws InternalException, InternalException
            {
                return new RecordColumnStorage(fields, beforeGet, isImmediateData);
            }

            @Override
            @OnThread(Tag.Simulation)
            public ColumnStorage<?> array(DataType inner) throws InternalException
            {
                return new ArrayColumnStorage(inner, (BeforeGet<ArrayColumnStorage>)beforeGet, isImmediateData);
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

    //@SuppressWarnings("valuetype")
    public static <T> @UnknownIfValue T unvalue(@Value T v)
    {
        return v;
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
    public static @Nullable @Value TemporalAccessor value(DateTimeInfo dest, @UnknownIfValue TemporalAccessor t)
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
        }
        return null;
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
        else if (item instanceof Number)
        {
            return Utility.numberToString((Number)item);
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
                String s = dateTimeInfo.getStrictFormatter().format(Utility.cast(item, TemporalAccessor.class));
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
                TaggedValue tv = Utility.cast(item, TaggedValue.class);
                String tagName = (asExpression ? ("@tag " + typeName.getRaw() + "\\") : "") + tags.get(tv.getTagIndex()).getName();
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
            public String record(ImmutableMap<@ExpressionIdentifier String, DataType> fields) throws InternalException, UserException
            {
                @Value Record record = Utility.cast(item, Record.class);
                StringBuilder s = new StringBuilder();
                if (parent == null || !isTagged(parent))
                    s.append("(");
                boolean first = true;
                for (Entry<@ExpressionIdentifier String, DataType> entry : Utility.iterableStream(fields.entrySet().stream().sorted(Comparator.comparing(e -> e.getKey()))))
                {
                    if (!first)
                        s.append(", ");
                    first = false;
                    s.append(entry.getKey()).append(": ");
                    s.append(valueToString(entry.getValue(), record.getField(entry.getKey()), dataType, asExpression));
                }
                if (parent == null || !isTagged(parent))
                    s.append(")");
                return s.toString();
            }
            
            private boolean isTagged(DataType type) throws InternalException
            {
                return type.apply(new FlatDataTypeVisitor<Boolean>(false) {
                    @Override
                    public Boolean tagged(TypeId typeName, ImmutableList typeVars, ImmutableList tags) throws InternalException, InternalException
                    {
                        return true;
                    }
                });
            }

            @Override
            @OnThread(Tag.Simulation)
            public String array(@Nullable DataType inner) throws InternalException, UserException
            {
                StringBuilder s = new StringBuilder("[");
                ListEx listEx = Utility.cast(item, ListEx.class);
                for (int i = 0; i < listEx.size(); i++)
                {
                    if (i != 0)
                        s.append(", ");
                    if (inner == null)
                        throw new InternalException("Array has empty type but is not empty");
                    s.append(valueToString(inner, listEx.get(i), dataType, asExpression));
                }
                return s.append("]").toString();
            }

            @Override
            public String function(ImmutableList<DataType> argType, DataType resultType) throws InternalException, UserException
            {
                return "<function>";
            }
        });
    }

    public static DataTypeValue listToType(DataType elementType, ListEx listEx) throws InternalException, UserException
    {
        return elementType.fromCollapsed((i, prog) -> listEx.get(i));
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
            public @Value Object record(ImmutableMap<@ExpressionIdentifier String, DataType> fields) throws InternalException, InternalException
            {
                ImmutableMap.Builder<@ExpressionIdentifier String, @Value Object> values = ImmutableMap.builderWithExpectedSize(fields.size());
                for (Entry<@ExpressionIdentifier String, DataType> entry : fields.entrySet())
                {
                    values.put(entry.getKey(), makeDefaultValue(entry.getValue()));
                }
                
                return DataTypeUtility.value(new RecordMap(values.build()));
            }

            @Override
            public @Value Object array(@Nullable DataType inner) throws InternalException, InternalException
            {
                return DataTypeUtility.value(Collections.emptyList());
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
            src.charStart = possibles.get(0).getFirst();
            @Nullable @Value TemporalAccessor value = value(dateTimeInfo, possibles.get(0).getSecond());
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
                src.charStart = chosen.getFirst();
                @Value TemporalAccessor value = value(dateTimeInfo, chosen.getSecond());
                if (value != null)
                    return value;
            }
            // If all the values of longest length are the same, that's fine:
            HashSet<Pair<Integer, TemporalAccessor>> distinctValues = new HashSet<>(
                possibles.stream().filter(p -> p.getFirst() == longest).collect(Collectors.<Pair<Integer, TemporalAccessor>>toList())
            );
            if (distinctValues.size() == 1)
            {
                Pair<Integer, TemporalAccessor> chosen = distinctValues.iterator().next();
                src.charStart = chosen.getFirst();
                @Value TemporalAccessor value = value(dateTimeInfo, chosen.getSecond());
                if (value != null)
                    return value;
            }
            
            // Otherwise, throw because it's too ambiguous:
            throw new UserException(Integer.toString(distinctValues.size()) + " ways to interpret " + dateTimeInfo + " value "
                + src.snippet() + ": "
                + Utility.listToString(Utility.<Pair<Integer, @Value TemporalAccessor>, @Value TemporalAccessor>mapList(possibles, p -> p.getSecond()))
                + " using formatters "
                + Utility.listToString(possibleFormatters));
        }

        //Log.debug("Wrapped: " + wrapped.toString() + " matches: " + possibles.size());
        throw new UserException("Expected " + DataType.date(dateTimeInfo).toString() + " value but found: " + src.snippet());
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
    // the substrings over and over.  The data is immutable, the position is mutable.
    public static class StringView
    {
        private static final ImmutableList<Integer> whiteSpaceCategories = ImmutableList.of(
            (int)Character.SPACE_SEPARATOR,
            (int)Character.LINE_SEPARATOR,
            (int)Character.PARAGRAPH_SEPARATOR,
            // Not really whitespace but contains \t and \n so we want to skip:
            (int)Character.CONTROL
        );
        
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
            while (charStart < original.length() && whiteSpaceCategories.contains(Character.getType(original.charAt(charStart))))
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
}
