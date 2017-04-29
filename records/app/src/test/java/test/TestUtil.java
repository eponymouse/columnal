package test;

import annotation.qual.Value;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.generator.java.lang.StringGenerator;
import com.pholser.junit.quickcheck.generator.java.time.LocalTimeGenerator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import one.util.streamex.StreamEx;
import one.util.streamex.StreamEx.Emitter;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Column;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.TableId;
import records.data.TableManager;
import records.data.datatype.DataTypeUtility;
import records.grammar.GrammarUtility;
import records.importers.ChoicePoint.ChoiceType;
import utility.TaggedValue;
import records.data.Transformation;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.datatype.DataType.NumberInfo;
import records.data.datatype.DataType.TagType;
import records.data.datatype.DataTypeValue;
import records.data.datatype.TypeManager;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.MainLexer;
import records.importers.ChoicePoint;
import records.importers.ChoicePoint.Choice;
import records.transformations.expression.Expression;
import records.transformations.expression.TypeState;
import test.gen.GenNumber;
import test.gen.GenZoneId;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExSupplier;
import utility.Pair;
import utility.Utility;
import utility.Utility.ListEx;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;

/**
 * Created by neil on 05/11/2016.
 */
public class TestUtil
{
    public static void assertEqualList(List<?> a, List<?> b)
    {
        if (a.size() != b.size())
            System.err.println("Different lengths");
        try
        {
            assertEquals(a, b);
        }
        catch (AssertionError e)
        {
            System.err.println("Content:");
            for (int i = 0; i < a.size(); i++)
            {
                System.err.println(((a.get(i) == null ? b.get(i) == null : a.get(i).equals(b.get(i))) ? "    " : "!!  ") + a.get(i) + "  " + b.get(i));
            }
            throw e;
        }

    }

    public static TableId generateTableId(SourceOfRandomness sourceOfRandomness)
    {
        return new TableId(generateIdent(sourceOfRandomness));
    }

    // Generates a pair of different ids
    public static Pair<TableId, TableId> generateTableIdPair(SourceOfRandomness r)
    {
        TableId us = generateTableId(r);
        TableId src;
        do
        {
            src = generateTableId(r);
        }
        while (src.equals(us));
        return new Pair<>(us, src);
    }

    public static ColumnId generateColumnId(SourceOfRandomness sourceOfRandomness)
    {
        return new ColumnId(generateIdent(sourceOfRandomness));
    }

    private static String generateIdent(SourceOfRandomness sourceOfRandomness)
    {
        if (sourceOfRandomness.nextBoolean())
        {
            List<String> keywords = getKeywords();
            return keywords.get(sourceOfRandomness.nextInt(0, keywords.size() - 1));
        }
        else
        {
            // These should be escaped, but would be blown away on load: "\n", "\r", "\t"
            return makeList(sourceOfRandomness, 1, 10, () -> sourceOfRandomness.choose(Arrays.asList(
                "a", "r", "n", "Z", "0", "9", "-", "=", "+", " ", "^", "@", "\"", "'"
            ))).stream().collect(Collectors.joining());
        }
    }

    private static List<String> getKeywords()
    {
        List<String> keywords = new ArrayList<>();

        keywords.add("@BEGIN");
        keywords.add("@END");

        for (int i = 0; i < MainLexer.VOCABULARY.getMaxTokenType(); i++)
        {
            String l = MainLexer.VOCABULARY.getLiteralName(i);
            if (l != null)
            {
                keywords.add(l.substring(1, l.length() - 1));
            }
        }
        return keywords;
    }

    public static <T> List<T> makeList(SourceOfRandomness r, int minSizeIncl, int maxSizeIncl, ExSupplier<T> makeOne)
    {
        try
        {
            int size = r.nextInt(minSizeIncl, maxSizeIncl);
            ArrayList<T> list = new ArrayList<>();
            for (int i = 0; i < size; i++)
                list.add(makeOne.get());
            return list;
        }
        catch (InternalException | UserException e)
        {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public static <K, V> Map<K, V> makeMap(SourceOfRandomness r, int minSizeIncl, int maxSizeIncl, Supplier<K> makeKey, Supplier<V> makeValue)
    {
        int size = r.nextInt(minSizeIncl, maxSizeIncl);
        HashMap<K, V> list = new HashMap<>();
        for (int i = 0; i < size; i++)
            list.put(makeKey.get(), makeValue.get());
        return list;
    }

    @OnThread(Tag.Simulation)
    public static StreamEx<List<@Value Object>> streamFlattened(RecordSet src)
    {
        return new StreamEx.Emitter<List<@Value Object>>()
        {
            int nextIndex = 0;
            @Override
            @OnThread(value = Tag.Simulation, ignoreParent = true)
            public @Nullable Emitter<List<@Value Object>> next(Consumer<? super List<Object>> consumer)
            {
                try
                {
                    if (src.indexValid(nextIndex))
                    {
                        List<@Value Object> collapsed = src.getColumns().stream().sorted(Comparator.comparing(Column::getName)).map(c ->
                        {
                            try
                            {
                                return c.getType().getCollapsed(nextIndex);
                            }
                            catch (UserException | InternalException e)
                            {
                                throw new RuntimeException(e);
                            }
                        }).collect(Collectors.toList());
                        consumer.accept(collapsed);
                        nextIndex += 1;
                        return this;
                    }
                    else
                        return null; // No more elements
                }
                catch (UserException | InternalException e)
                {
                    throw new RuntimeException(e);
                }
            }
        }.stream();
    }

    @OnThread(Tag.Simulation)
    public static StreamEx<@Value Object> streamFlattened(Column column)
    {
        return new StreamEx.Emitter<@Value Object>()
        {
            int nextIndex = 0;
            @Override
            @OnThread(value = Tag.Simulation, ignoreParent = true)
            public @Nullable Emitter<@Value Object> next(Consumer<? super @Value Object> consumer)
            {
                try
                {
                    if (column.indexValid(nextIndex))
                    {
                        Object collapsed = column.getType().getCollapsed(nextIndex);
                        consumer.accept(collapsed);
                        nextIndex += 1;
                        return this;
                    }
                    else
                        return null; // No more elements
                }
                catch (UserException | InternalException e)
                {
                    throw new RuntimeException(e);                }
            }
        }.stream();
    }

    public static <T> List<T> makeList(int len, Generator<? extends T> gen, SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
    {
        return Stream.generate(() -> gen.generate(sourceOfRandomness, generationStatus)).limit(len).collect(Collectors.toList());
    }

    @OnThread(Tag.Simulation)
    @SuppressWarnings("nullness")
    public static Map<List<@Value Object>, Long> getRowFreq(RecordSet src)
    {
        SortedMap<List<@Value Object>, Long> r = new TreeMap<>((Comparator<List<@Value Object>>)(List<@Value Object> a, List<@Value Object> b) -> {
            if (a.size() != b.size())
                return b.size() - a.size();
            for (int i = 0; i < a.size(); i++)
            {
                try
                {
                    int cmp = Utility.compareValues(a.get(i), b.get(i));
                    if (cmp != 0)
                        return cmp;
                }
                catch (InternalException | UserException e)
                {
                    throw new RuntimeException(e);
                }
            }
            return 0;
        });
        streamFlattened(src).forEach((List<@Value Object> row) -> {
            r.compute(row, (List<@Value Object> k , Long v) -> v == null ? 1 : v + 1);
        });
        return r;
    }

    public static @Value String makeStringV(SourceOfRandomness r, GenerationStatus gs)
    {
        return DataTypeUtility.value(makeString(r, gs));
    }

    public static String makeString(SourceOfRandomness r, @Nullable GenerationStatus gs)
    {
        // Makes either totally random String from generator, or "awkward" string
        // with things likely to trip up parser
        if (r.nextBoolean() && gs != null)
            return new StringGenerator().generate(r, gs);
        else
            return generateIdent(r);
    }

    public static String makeNonEmptyString(SourceOfRandomness r, GenerationStatus gs)
    {
        String s;
        do
        {
            s = makeString(r, gs);
        }
        while (s.isEmpty());
        return s;
    }

    public static String makeUnquotedIdent(SourceOfRandomness r, GenerationStatus gs)
    {
        String s;
        do
        {
            s = makeString(r, gs);
        }
        while (!GrammarUtility.validUnquoted(s));
        return s;
    }

    public static List<ColumnId> generateColumnIds(SourceOfRandomness r, int numColumns)
    {
        List<ColumnId> columnIds = new ArrayList<>();
        while (columnIds.size() < numColumns)
        {
            ColumnId c = generateColumnId(r);
            if (!columnIds.contains(c))
                columnIds.add(c);
        }
        return columnIds;
    }

    public static List<DataType> distinctTypes;
    static {
        try
        {
            DataType a = DummyManager.INSTANCE.getTypeManager().registerTaggedType("A", Arrays.asList(new TagType<DataType>("Single", null)));
            DataType c = DummyManager.INSTANCE.getTypeManager().registerTaggedType("C", Arrays.asList(new TagType<DataType>("Blank", null), new TagType<DataType>("Number", DataType.NUMBER)));
            distinctTypes = Arrays.<DataType>asList(
                DataType.BOOLEAN,
                DataType.TEXT,
                DataType.date(new DateTimeInfo(DateTimeType.YEARMONTHDAY)),
                DataType.date(new DateTimeInfo(DateTimeType.YEARMONTH)),
                DataType.date(new DateTimeInfo(DateTimeType.DATETIME)),
                DataType.date(new DateTimeInfo(DateTimeType.DATETIMEZONED)),
                DataType.date(new DateTimeInfo(DateTimeType.TIMEOFDAY)),
                DataType.date(new DateTimeInfo(DateTimeType.TIMEOFDAYZONED)),
                DataType.NUMBER,
                DataType.number(new NumberInfo(DummyManager.INSTANCE.getUnitManager().loadUse("m"), 0)),
                DataType.number(new NumberInfo(DummyManager.INSTANCE.getUnitManager().loadUse("m^2"), 0)),
                DataType.number(new NumberInfo(DummyManager.INSTANCE.getUnitManager().loadUse("m^3/s^3"), 0)),
                DataType.number(new NumberInfo(DummyManager.INSTANCE.getUnitManager().loadUse("cm"), 0)),
                DataType.number(new NumberInfo(DummyManager.INSTANCE.getUnitManager().loadUse("($ m)/s^2"), 0)),
                a,
                DummyManager.INSTANCE.getTypeManager().registerTaggedType("B", Arrays.asList(new TagType<DataType>("Single ", null))),
                c,
                DummyManager.INSTANCE.getTypeManager().registerTaggedType("Nested", Arrays.asList(new TagType<DataType>("A", a), new TagType<DataType>("C", c))),
                DataType.tuple(Arrays.asList(DataType.NUMBER, DataType.NUMBER)),
                DataType.tuple(Arrays.asList(DataType.BOOLEAN, DataType.TEXT, DataType.date(new DateTimeInfo(DateTimeType.TIMEOFDAYZONED)), c)),
                DataType.tuple(Arrays.asList(DataType.NUMBER, DataType.tuple(Arrays.asList(DataType.TEXT, DataType.NUMBER)))),
                DataType.array(DataType.TEXT),
                DataType.array(DataType.NUMBER),
                DataType.array(DataType.tuple(Arrays.asList(DataType.NUMBER, DataType.tuple(Arrays.asList(DataType.TEXT, DataType.NUMBER))))),
                DataType.array(DataType.array(DataType.tuple(Arrays.asList(DataType.NUMBER, DataType.tuple(Arrays.asList(DataType.TEXT, DataType.NUMBER))))))
            );
        }
        catch (UserException | InternalException e)
        {
            throw new RuntimeException(e);
        }
    }
    private static Pair<@Nullable String, DataType> t(DataType type)
    {
        return new Pair<>(null, type);
    }

    @SuppressWarnings("nullness")
    public static TypeState typeState()
    {
        try
        {
            UnitManager unitManager = new UnitManager();
            TypeManager typeManager = new TypeManager(unitManager);
            distinctTypes.stream().filter(p -> p.isTagged()).forEach(t -> {
                try
                {
                    typeManager.registerTaggedType(t.getTaggedTypeName().getRaw(), t.getTagTypes());
                }
                catch (InternalException e)
                {
                    throw new RuntimeException(e);
                }
            });
            return new TypeState(unitManager, typeManager);
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }

    public static String generateVarName(SourceOfRandomness r)
    {
        String s;
        do
        {
            s = generateIdent(r);
        }
        while (!GrammarUtility.validUnquoted(s));
        return s;
    }

    public static @Value Number generateNumberV(SourceOfRandomness r, GenerationStatus gs)
    {
        return DataTypeUtility.value(generateNumber(r, gs));
    }

    public static Number generateNumber(SourceOfRandomness r, GenerationStatus gs)
    {
        return new GenNumber().generate(r, gs);
    }

    private static LocalDate MIN_DATE = LocalDate.of(1, 1, 1);
    private static LocalDate MAX_DATE = LocalDate.of(9999, 12, 31);

    public static @Value LocalDate generateDate(SourceOfRandomness r, GenerationStatus gs)
    {
        return (LocalDate) DataTypeUtility.value(new DateTimeInfo(DateTimeType.YEARMONTHDAY), LocalDate.ofEpochDay(r.nextLong(MIN_DATE.toEpochDay(), MAX_DATE.toEpochDay())));
    }

    public static @Value LocalTime generateTime(SourceOfRandomness r, GenerationStatus gs)
    {
        return (LocalTime) DataTypeUtility.value(new DateTimeInfo(DateTimeType.TIMEOFDAY), new LocalTimeGenerator().generate(r, gs));
    }

    public static @Value LocalDateTime generateDateTime(SourceOfRandomness r, GenerationStatus gs)
    {
        return LocalDateTime.of(generateDate(r, gs), generateTime(r, gs));
    }

    public static @Value ZonedDateTime generateDateTimeZoned(SourceOfRandomness r, GenerationStatus gs)
    {
        return (ZonedDateTime) DataTypeUtility.value(new DateTimeInfo(DateTimeType.DATETIMEZONED), ZonedDateTime.of(generateDateTime(r, gs), generateZone(r, gs)));
    }

    public static ZoneId generateZone(SourceOfRandomness r, GenerationStatus gs)
    {
        return new GenZoneId().generate(r, gs);
    }

    public static ZoneOffset generateZoneOffset(SourceOfRandomness r, GenerationStatus gs)
    {
        return ZoneOffset.ofTotalSeconds(r.nextInt(-12*60*60, 12*60*60));
    }

    public static String generateZoneString(SourceOfRandomness r, GenerationStatus gs)
    {
        return generateZone(r, gs).toString();
    }

    @OnThread(Tag.Simulation)
    public static String toString(@Value Object value)
    {
        if (value instanceof Object[])
        {
            return "(" + Arrays.stream((@Value Object[])value).map(TestUtil::toString).collect(Collectors.joining(",")) + ")";
        }
        else if (value instanceof ListEx)
        {
            StringBuilder sb = new StringBuilder("[");
            ListEx list = (ListEx) value;
            try
            {
                for (int i = 0; i < list.size(); i++)
                {
                    if (i != 0)
                        sb.append(", ");
                    sb.append(toString(list.get(i)));
                }
            }
            catch (InternalException | UserException e)
            {
                sb.append("ERROR...");
            }
            sb.append("]");
            return sb.toString();
        }
        else if (value instanceof TaggedValue)
        {
            TaggedValue t = ((TaggedValue)value);
            return t.getTagIndex() + (t.getInner() == null ? "" : ":" + toString(t.getInner()));
        }
        else
            return value.toString();
    }

    @OnThread(Tag.Simulation)
    public static String toString(Column c) throws UserException, InternalException
    {
        StringBuilder sb = new StringBuilder("[");
        DataTypeValue t = c.getType();
        for (int i = 0; i < c.getLength(); i++)
        {
            if (i != 0)
                sb.append(", ");
            sb.append(toString(t.getCollapsed(i)));
        }
        sb.append("]");
        return sb.toString();
    }

    public static class ChoicePick<C extends Choice>
    {
        private final Class<C> theClass;
        private final C choice;

        ChoicePick(Class<C> theClass, C choice)
        {
            this.theClass = theClass;
            this.choice = choice;
        }
    }

    @SuppressWarnings("i18n") // No idea why this triggers here
    public static <C extends Choice, R> @NonNull R pick(ChoicePoint<C, R> choicePoint, ChoicePick<C>... picks) throws InternalException, UserException
    {
        ChoiceType<C> choicePointType = choicePoint.getChoiceType();
        if (choicePointType == null)
        {
            return choicePoint.get();
        }
        else
        {
            for (ChoicePick<C> pick : picks)
            {
                if (pick.theClass.equals(choicePointType.getChoiceClass()))
                {
                    ChoicePoint<Choice, R> selected = (ChoicePoint<Choice, R>) choicePoint.select(pick.choice);
                    @NonNull R picked = pick(selected, (ChoicePick<Choice>[]) picks);
                    return picked;
                }
            }
            throw new RuntimeException("No suitable choice for class: " + choicePointType);
        }
    }

    public static class Transformation_Mgr
    {
        public final TableManager mgr;
        public final Transformation transformation;

        public Transformation_Mgr(TableManager mgr, Transformation transformation)
        {
            this.mgr = mgr;
            this.transformation = transformation;
        }

        @Override
        @OnThread(value = Tag.Simulation, ignoreParent = true) // Only for testing anyway
        public String toString()
        {
            return transformation.toString();
        }
    }

    public static class Expression_Mgr
    {
        public final TableManager mgr;
        public final Expression expression;

        public Expression_Mgr(TableManager mgr, Expression expression)
        {
            this.mgr = mgr;
            this.expression = expression;
        }
    }
}
