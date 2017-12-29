package test;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.generator.java.time.LocalTimeGenerator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import com.sun.javafx.PlatformUtil;
import javafx.application.Platform;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import one.util.streamex.StreamEx;
import one.util.streamex.StreamEx.Emitter;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.testfx.api.FxRobotInterface;
import org.testfx.util.WaitForAsyncUtils;
import records.data.Column;
import records.data.ColumnId;
import records.data.EditableRecordSet;
import records.data.ImmediateDataSource;
import records.data.RecordSet;
import records.data.Table;
import records.data.Table.FullSaver;
import records.data.TableId;
import records.data.TableManager;
import records.data.datatype.DataType.DataTypeVisitor;
import records.data.datatype.DataType.DataTypeVisitorEx;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.TypeId;
import records.grammar.GrammarUtility;
import records.gui.MainWindow;
import records.importers.ChoicePoint.ChoiceType;
import records.transformations.expression.ErrorRecorder;
import records.transformations.function.FunctionDefinition;
import records.transformations.function.FunctionDefinition.FunctionTypes;
import records.transformations.function.FunctionInstance;
import records.types.TypeExp;
import test.gen.GenString;
import utility.*;
import records.data.Transformation;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.datatype.NumberInfo;
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
import utility.Utility.ListEx;
import utility.Workers.Priority;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Created by neil on 05/11/2016.
 */
public class TestUtil
{
    public static final @LocalizableKey String EMPTY_KEY = makeEmptyKey();

    private static @LocalizableKey String makeEmptyKey()
    {
        // I cannot seem to get the checker to suppress warnings, so instead give a key that is valid:
        return "menu.exit";
    }

    @OnThread(Tag.Any)
    public static void assertValueEqual(String prefix, @Value Object a, @Value Object b) throws UserException, InternalException
    {
        try
        {
            CompletableFuture<Integer> f = new CompletableFuture<>();
            Workers.onWorkerThread("Comparison", Priority.FETCH, () -> checkedToRuntime(() -> f.complete(Utility.compareValues(a, b))));
            int compare = f.get();
            if (compare != 0)
            {
                fail(prefix + " comparing " + DataTypeUtility._test_valueToString(a) + " against " + DataTypeUtility._test_valueToString(b) + " result: " + compare);
            }
        }
        catch (ExecutionException | InterruptedException e)
        {
            throw new RuntimeException(e);
        }
    }

    @OnThread(Tag.Any)
    public static void assertValueListEqual(String prefix, List<@Value Object> a, List<@Value Object> b) throws UserException, InternalException
    {
        assertEquals(prefix + " list size", a.size(), b.size());
        for (int i = 0; i < a.size(); i++)
        {
            assertValueEqual(prefix, a.get(i), b.get(i));
        }
    }

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
            String trimmed = TestUtil.<@NonNull String>makeList(sourceOfRandomness, 1, 10, () -> sourceOfRandomness.<@NonNull String>choose(Arrays.asList(
                "a", "r", "n", "Z", "0", "9", "-", "=", "+", " ", "^", "@", "\"", "'"
            ))).stream().collect(Collectors.joining()).trim();
            if (trimmed.isEmpty())
                return "a";
            else
                return trimmed;
        }
    }

    private static List<String> getKeywords()
    {
        List<String> keywords = new ArrayList<>();

        keywords.add("Z"); // Likely to cause issues for date/time parsing.
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

    public static <T> ImmutableList<T> makeList(SourceOfRandomness r, int minSizeIncl, int maxSizeIncl, ExSupplier<T> makeOne)
    {
        try
        {
            int size = r.nextInt(minSizeIncl, maxSizeIncl);
            ImmutableList.Builder<T> list = ImmutableList.builder();
            for (int i = 0; i < size; i++)
                list.add(makeOne.get());
            return list.build();
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
        return DataTypeUtility.value(makeString(r, gs).replaceAll("\"", ""));
    }

    public static String makeString(SourceOfRandomness r, @Nullable GenerationStatus gs)
    {
        // Makes either totally random String from generator, or "awkward" string
        // with things likely to trip up parser
        if (r.nextBoolean() && gs != null)
        {
            return new GenString().generate(r, gs);
        }
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
        while (s.trim().isEmpty());
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
            // TODO add a higher-order type
            DataType a = DummyManager.INSTANCE.getTypeManager().registerTaggedType("A", ImmutableList.of(), ImmutableList.of(new TagType<DataType>("Single", null))).instantiate(ImmutableList.of());
            DataType c = DummyManager.INSTANCE.getTypeManager().registerTaggedType("C", ImmutableList.of(), ImmutableList.of(new TagType<DataType>("Blank", null), new TagType<DataType>("Number", DataType.NUMBER))).instantiate(ImmutableList.of());
            DataType b = DummyManager.INSTANCE.getTypeManager().registerTaggedType("B", ImmutableList.of(), ImmutableList.of(new TagType<DataType>("Single", null))).instantiate(ImmutableList.of());
            DataType nested = DummyManager.INSTANCE.getTypeManager().registerTaggedType("Nested", ImmutableList.of(), ImmutableList.of(new TagType<DataType>("A", a), new TagType<DataType>("C", c))).instantiate(ImmutableList.of());
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
                DataType.number(new NumberInfo(DummyManager.INSTANCE.getUnitManager().loadUse("m"), null)),
                DataType.number(new NumberInfo(DummyManager.INSTANCE.getUnitManager().loadUse("m^2"), null)),
                DataType.number(new NumberInfo(DummyManager.INSTANCE.getUnitManager().loadUse("m^3/s^3"), null)),
                DataType.number(new NumberInfo(DummyManager.INSTANCE.getUnitManager().loadUse("cm"), null)),
                DataType.number(new NumberInfo(DummyManager.INSTANCE.getUnitManager().loadUse("($*m)/s^2"), null)),
                a,
                b,
                c,
                nested,
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
            List<DataType> taggedTypes = distinctTypes.stream().filter(p -> p.isTagged()).collect(Collectors.toList());
            for (DataType t : taggedTypes)
            {
                typeManager.registerTaggedType(t.getTaggedTypeName().getRaw(), ImmutableList.of(), t.getTagTypes());
            }
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
    private static LocalDate MIN_CLOSE_DATE = LocalDate.of(1900, 1, 1);
    private static LocalDate MAX_CLOSE_DATE = LocalDate.of(2050, 12, 31);

    public static @Value LocalDate generateDate(SourceOfRandomness r, GenerationStatus gs)
    {
        // Usually, generate dates in sensible range (1900->2050)
        if (r.nextInt(4) != 0)
            return (LocalDate) DataTypeUtility.value(new DateTimeInfo(DateTimeType.YEARMONTHDAY), LocalDate.ofEpochDay(r.nextLong(MIN_CLOSE_DATE.toEpochDay(), MAX_CLOSE_DATE.toEpochDay())));
        else
            return (LocalDate) DataTypeUtility.value(new DateTimeInfo(DateTimeType.YEARMONTHDAY), LocalDate.ofEpochDay(r.nextLong(MIN_DATE.toEpochDay(), MAX_DATE.toEpochDay())));
    }

    public static @Value LocalTime generateTime(SourceOfRandomness r, GenerationStatus gs)
    {
        LocalTime localTime = (LocalTime) DataTypeUtility.value(new DateTimeInfo(DateTimeType.TIMEOFDAY), new LocalTimeGenerator().generate(r, gs));
        // Produce half the dates without partial seconds (and perhaps seconds set to zero): common case
        if (r.nextBoolean())
        {
            localTime = localTime.minusNanos(localTime.getNano());
            if (r.nextBoolean())
                localTime = localTime.minusSeconds(localTime.getSecond());
        }
        @SuppressWarnings("value")
        @Value LocalTime ret = localTime;
        return ret;
    }

    public static @Value LocalDateTime generateDateTime(SourceOfRandomness r, GenerationStatus gs)
    {
        return LocalDateTime.of(generateDate(r, gs), generateTime(r, gs));
    }

    public static @Value ZonedDateTime generateDateTimeZoned(SourceOfRandomness r, GenerationStatus gs)
    {
        return (ZonedDateTime) DataTypeUtility.value(new DateTimeInfo(DateTimeType.DATETIMEZONED), ZonedDateTime.of(generateDateTime(r, gs), r.nextBoolean() ? generateZone(r, gs) : generateZoneOffset(r, gs)));
    }

    public static ZoneId generateZone(SourceOfRandomness r, GenerationStatus gs)
    {
        return new GenZoneId().generate(r, gs);
    }

    public static ZoneOffset generateZoneOffset(SourceOfRandomness r, GenerationStatus gs)
    {
        // We don't support sub-minute offsets:
        return ZoneOffset.ofTotalSeconds(r.nextInt(-12*60, 12*60) * 60);
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

    @OnThread(Tag.Any)
    public static <T> T fx(FXPlatformSupplierEx<T> action)
    {
        try
        {
            return WaitForAsyncUtils.asyncFx(action).get();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    @OnThread(Tag.Any)
    public static void fx_(FXPlatformRunnable action)
    {
        try
        {
            WaitForAsyncUtils.asyncFx(action::run).get();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    @OnThread(Tag.Any)
    public static <T> T sim(SimulationSupplier<T> action)
    {
        try
        {
            CompletableFuture<Either<Throwable, T>> f = new CompletableFuture<>();
            Workers.onWorkerThread("Test.sim " + Thread.currentThread().getStackTrace()[2].getClassName() + "." + Thread.currentThread().getStackTrace()[2].getMethodName() + ":" + Thread.currentThread().getStackTrace()[2].getLineNumber(), Priority.FETCH, () -> {
                try
                {
                    f.complete(Either.right(action.get()));
                }
                catch (Throwable e)
                {
                    f.complete(Either.left(e));
                }
            });
            return f.get(60, TimeUnit.SECONDS).either(e -> {throw new RuntimeException(e);}, x -> x);
        }
        catch (TimeoutException e)
        {
            throw new RuntimeException(Workers._test_getCurrentTaskName(), e);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    @OnThread(Tag.Any)
    public static void sim_(SimulationRunnable action)
    {
        try
        {
            CompletableFuture<Either<Throwable, Object>> f = new CompletableFuture<>();
            Workers.onWorkerThread("Test.sim_ " + Thread.currentThread().getStackTrace()[2].getClassName() + "." + Thread.currentThread().getStackTrace()[2].getMethodName() + ":" + Thread.currentThread().getStackTrace()[2].getLineNumber(), Priority.FETCH, () -> {
                try
                {
                    action.run();
                    f.complete(Either.right(new Object()));
                }
                catch (Throwable e)
                {
                    f.complete(Either.left(e));
                }
            });
            f.get(60, TimeUnit.SECONDS).either_(e -> {throw new RuntimeException(e);}, x -> {});
        }
        catch (TimeoutException e)
        {
            throw new RuntimeException(Workers._test_getCurrentTaskName(), e);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    // Registers all tagged types contained within, recursively
    public static void registerAllTaggedTypes(TypeManager typeManager, DataType outer) throws InternalException, UserException
    {
        outer.apply(new DataTypeVisitor<@Nullable Void>()
        {
            @Override
            public @Nullable Void number(NumberInfo numberInfo) throws InternalException, UserException
            {
                return null;
            }

            @Override
            public @Nullable Void text() throws InternalException, UserException
            {
                return null;
            }

            @Override
            public @Nullable Void date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
            {
                return null;
            }

            @Override
            public @Nullable Void bool() throws InternalException, UserException
            {
                return null;
            }

            @Override
            public @Nullable Void tagged(TypeId typeName, ImmutableList<DataType> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, UserException
            {
                typeManager.registerTaggedType(typeName.getRaw(), ImmutableList.of(), tags);
                for (TagType<DataType> tag : tags)
                {
                    if (tag.getInner() != null)
                    {
                        registerAllTaggedTypes(typeManager, tag.getInner());
                    }
                }
                return null;
            }

            @Override
            public @Nullable Void tuple(ImmutableList<DataType> inner) throws InternalException, UserException
            {
                for (DataType dataType : inner)
                {
                    registerAllTaggedTypes(typeManager, dataType);
                }
                return null;
            }

            @Override
            public @Nullable Void array(@Nullable DataType inner) throws InternalException, UserException
            {
                if (inner != null)
                    registerAllTaggedTypes(typeManager, inner);
                return null;
            }
        });
    }

    @OnThread(Tag.Simulation)
    public static String save(TableManager tableManager) throws ExecutionException, InterruptedException, InvocationTargetException
    {
        // This thread is only pretend running on FXPlatform, but sets off some
        // code which actually runs on the fx platform thread:
        CompletableFuture<String> f = new CompletableFuture<>();

        try
        {
            FullSaver saver = new FullSaver();
            tableManager.save(null, saver);
            f.complete(saver.getCompleteFile());
        }
        catch (Throwable t)
        {
            t.printStackTrace();
            f.complete("");
        }
        return f.get();
    }

    public static <T> T checkedToRuntime(ExSupplier<T> supplier)
    {
        try
        {
            return supplier.get();
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }

    public static void checkedToRuntime_(ExRunnable runnable)
    {
        try
        {
            runnable.run();
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }

    public static void sleep(int millis)
    {
        try
        {
            Thread.sleep(millis);
        }
        catch (InterruptedException e)
        {

        }
    }

    /**
     * Removes a random item from the given list and returns it.
     * Note that the list is modified!
     */
    public static <T> T removeRandom(Random r, List<T> list)
    {
        int index = r.nextInt(list.size());
        return list.remove(index);
    }

    /**
     * IMPORTANT: we say Simulation thread to satisfy thread-checker, but don't call it from the actual
     * simultation thread or it will time out!  Just tag yours as simulation, too.
     *
     * Returns a runnable which will wait for the table to load
     *
     * @param windowToUse
     * @param mgr
     * @throws IOException
     * @throws InterruptedException
     * @throws ExecutionException
     * @throws InvocationTargetException
     */
    @OnThread(Tag.Simulation)
    public static Runnable openDataAsTable(Stage windowToUse, TableManager mgr) throws IOException, InterruptedException, ExecutionException, InvocationTargetException
    {
        File temp = File.createTempFile("srcdata", "tables");
        temp.deleteOnExit();
        String saved = save(mgr);
        System.out.println("Saving: {{{" + saved + "}}}");
        Platform.runLater(() -> checkedToRuntime_(() -> MainWindow.show(windowToUse, temp, new Pair<>(temp, saved))));
        return () -> {
            do
            {
                //System.err.println("Waiting for main window");
                sleep(1000);
            }
            while (fx(() -> windowToUse.getScene().lookup(".id-tableDisplay-menu-button")) == null);
        };
    }

    // WOuld be nice to get this working, but doesn't currently work
    public static void writePaste_doesntwork(FxRobotInterface robot, String string)
    {
        fx_(() -> Clipboard.getSystemClipboard().setContent(Collections.singletonMap(DataFormat.PLAIN_TEXT, string)));
        robot.push(PlatformUtil.isMac() ? KeyCode.COMMAND : KeyCode.CONTROL, KeyCode.V);
    }

    /*public static interface TablesMaker
    {
        public List<Table>
    }*/

    @OnThread(Tag.Simulation)
    public static void openDataAsTable(Stage windowToUse, @Nullable TypeManager typeManager, RecordSet data) throws IOException, InterruptedException, ExecutionException, InvocationTargetException, UserException, InternalException
    {
        TableManager manager = new DummyManager();
        Table t = new ImmediateDataSource(manager, new EditableRecordSet(data));
        manager.record(t);
        if (typeManager != null)
        {
            manager.getTypeManager()._test_copyTaggedTypesFrom(typeManager);
        }
        openDataAsTable(windowToUse, manager).run();
    }

    // Makes something which could be an unfinished expression.  Can't have operators, can't start with a number.
    public static String makeUnfinished(SourceOfRandomness r)
    {
        StringBuilder s = new StringBuilder();
        s.append(r.nextChar('a', 'z'));
        int len = r.nextInt(0, 10);
        for (int i = 0; i < len; i++)
        {
            s.append(r.nextBoolean() ? r.nextChar('a', 'z') : r.nextChar('0', '9'));
        }
        return s.toString();
    }

    @OnThread(Tag.Simulation)
    public static void assertUserException(SimulationRunnable simulationRunnable)
    {
        try
        {
            simulationRunnable.run();
            // If we reach here, didn't throw:
            fail("Expected UserException but no exception thrown");
        }
        catch (UserException e)
        {
            // As expected:
            return;
        }
        catch (InternalException e)
        {
            throw new RuntimeException(e);
        }
    }

    // Wait.  Useful to stop multiple consecutive clicks turning into double clicks
    public static void delay(int millis)
    {
        try
        {
            Thread.sleep(millis);
        }
        catch (InterruptedException e)
        {

        }
    }

    public static void assertEqualsText(String prefix, String expected, String actual)
    {
        if (!expected.equals(actual))
        {
            String[] expectedLines = expected.split("\n");
            String[] actualLines = actual.split("\n");
            for (int i = 0; i < Math.max(expectedLines.length, actualLines.length); i++)
            {
                String expectedLine = i < expectedLines.length ? expectedLines[i] : null;
                String actualLine = i < actualLines.length ? actualLines[i] : null;
                assertEquals(prefix + "\nExpected line " + i + ": " + (expectedLine == null ? "null" : stringAsHexChars(expectedLine)) + "\nActual: " + (actualLine == null ? "null" : stringAsHexChars(actualLine)), expectedLine, actualLine);
            }
        }
    }

    private static String stringAsHexChars(String str)
    {
        return str.chars().mapToObj(c -> Integer.toHexString(c) + (c == 10 ? "\n" : "")).collect(Collectors.joining(" "));
    }

    public static @Nullable Pair<FunctionInstance,DataType> typeCheckFunction(FunctionDefinition function, List<Object> units, DataType paramType) throws InternalException, UserException
    {
        return typeCheckFunction(function, units, paramType, null);
    }

    public static @Nullable Pair<FunctionInstance,DataType> typeCheckFunction(FunctionDefinition function, List<Object> units, DataType paramType, @Nullable TypeManager overrideTypeManager) throws InternalException, UserException
    {
        ErrorRecorder onError = (src, err, fixes) -> {throw new RuntimeException(err);};
        TypeManager typeManager = overrideTypeManager != null ? overrideTypeManager : DummyManager.INSTANCE.getTypeManager();
        FunctionTypes functionTypes = function.makeParamAndReturnType(typeManager);
        @SuppressWarnings("nullness") // For null src
        TypeExp paramTypeExp = onError.recordError(null, TypeExp.unifyTypes(functionTypes.paramType, TypeExp.fromConcrete(null, paramType)));
        if (paramTypeExp == null)
            return null;
        
        @SuppressWarnings("nullness") // For null src
        @Nullable DataType returnType = onError.recordError(null, functionTypes.returnType.toConcreteType(typeManager));
        if (returnType != null)
            return new Pair<>(functionTypes.getInstanceAfterTypeCheck(), returnType);
        return null;
    }

    public static @Nullable Pair<FunctionInstance,DataType> typeCheckFunction(FunctionDefinition function, DataType expectedReturnType, List<Object> units, DataType paramType, @Nullable TypeManager overrideTypeManager) throws InternalException, UserException
    {
        ErrorRecorder onError = (src, err, fixes) -> {throw new RuntimeException(err);};
        TypeManager typeManager = overrideTypeManager != null ? overrideTypeManager : DummyManager.INSTANCE.getTypeManager();
        FunctionTypes functionTypes = function.makeParamAndReturnType(typeManager);
        @SuppressWarnings("nullness") // For null src
        TypeExp paramTypeExp = onError.recordError(null, TypeExp.unifyTypes(functionTypes.paramType, TypeExp.fromConcrete(null, paramType)));
        if (paramTypeExp == null)
            return null;
        @SuppressWarnings("nullness") // For null src
        @Nullable DataType returnType = onError.recordError(null, TypeExp.unifyTypes(TypeExp.fromConcrete(null, expectedReturnType), functionTypes.returnType).flatMapEx(t -> t.toConcreteType(typeManager)));
        if (returnType != null)
            return new Pair<>(functionTypes.getInstanceAfterTypeCheck(), returnType);
        return null;
    }

    public static void assertNoTypeVariables(DataType dataType) throws InternalException
    {
        dataType.apply(new DataTypeVisitorEx<UnitType,InternalException>()
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
            public UnitType tagged(TypeId typeName, ImmutableList<DataType> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, InternalException
            {
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
            public UnitType array(@Nullable DataType inner) throws InternalException, InternalException
            {
                if (inner != null)
                    inner.apply(this);
                return UnitType.UNIT;
            }

            @Override
            public UnitType typeVariable(String typeVariableName) throws InternalException, InternalException
            {
                fail("Found type variable in type: " + dataType);
                return UnitType.UNIT;
            }
        });
    }

    public static interface FXPlatformSupplierEx<T> extends Callable<T>
    {
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        public T call() throws InternalException, UserException;
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

    @SuppressWarnings("all") // I18n triggers here, and i18n alone won't shut it off. No idea what the issue is.
    @SafeVarargs
    public static <C extends Choice, R> @NonNull R pick(ChoicePoint<C, R> choicePoint, ChoicePick<C>... picks) throws InternalException, UserException
    {
        ChoiceType<C> choicePointType = choicePoint.getOptions() == null ? null : choicePoint.getOptions().choiceType;
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
                    @SuppressWarnings("unchecked")
                    ChoicePoint<Choice, R> selected = (ChoicePoint<Choice, R>) choicePoint.select(pick.choice);
                    @SuppressWarnings("unchecked")
                    ChoicePick<Choice>[] picksCast = (ChoicePick<Choice>[]) picks;
                    @NonNull R picked = pick(selected, picksCast);
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

        @OnThread(Tag.Simulation)
        public Transformation_Mgr(TableManager mgr, Transformation transformation)
        {
            this.mgr = mgr;
            this.transformation = transformation;
            mgr.record(transformation);
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
