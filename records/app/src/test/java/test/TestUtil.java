package test;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.generator.java.time.LocalTimeGenerator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import com.sun.javafx.PlatformUtil;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import log.Log;
import one.util.streamex.StreamEx;
import one.util.streamex.StreamEx.Emitter;
import org.apache.commons.lang3.SystemUtils;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.testfx.api.FxRobotInterface;
import org.testfx.util.WaitForAsyncUtils;
import records.data.*;
import records.data.Table.FullSaver;
import records.data.Table.InitialLoadDetails;
import records.data.datatype.DataType.DataTypeVisitor;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.TypeId;
import records.data.datatype.TypeManager.TagInfo;
import records.data.unit.Unit;
import records.grammar.GrammarUtility;
import records.gui.MainWindow;
import records.gui.MainWindow.MainWindowActions;
import records.jellytype.JellyType;
import records.transformations.expression.CallExpression;
import records.transformations.expression.ConstructorExpression;
import records.transformations.expression.ErrorAndTypeRecorder;
import records.transformations.expression.EvaluateState;
import records.transformations.expression.QuickFix;
import records.transformations.expression.TypeState;
import records.transformations.function.FunctionDefinition;
import records.typeExp.MutVar;
import records.typeExp.TypeCons;
import records.typeExp.TypeExp;
import records.typeExp.units.MutUnitVar;
import styled.StyledShowable;
import styled.StyledString;
import test.gen.GenString;
import utility.*;
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
import records.transformations.expression.Expression;
import test.gen.GenNumber;
import test.gen.GenZoneId;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility.ListEx;
import utility.Workers.Priority;
import utility.gui.FXUtility;

import javax.imageio.ImageIO;
import java.awt.AWTException;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeNoException;

/**
 * Created by neil on 05/11/2016.
 */
public class TestUtil
{
    public static final @LocalizableKey String EMPTY_KEY = makeEmptyKey();
    public static final InitialLoadDetails ILD = new InitialLoadDetails(null, null, null);

    private static @LocalizableKey String makeEmptyKey()
    {
        // I cannot seem to get the checker to suppress warnings, so instead give a key that is valid:
        return "menu.exit";
    }

    @OnThread(Tag.Any)
    public static void assertValueEqual(String prefix, @Nullable @Value Object a, @Nullable @Value Object b) throws UserException, InternalException
    {
        try
        {
            if ((a == null) != (b == null))
                fail(prefix + " differing nullness: " + (a == null) + " vs " + (b == null));
            
            if (a != null && b != null)
            {
                @NonNull @Value Object aNN = a;
                @NonNull @Value Object bNN = b;
                CompletableFuture<Integer> f = new CompletableFuture<>();
                Workers.onWorkerThread("Comparison", Priority.FETCH, () -> checkedToRuntime(() -> f.complete(Utility.compareValues(aNN, bNN))));
                int compare = f.get();
                if (compare != 0)
                {
                    fail(prefix + " comparing " + DataTypeUtility._test_valueToString(a) + " against " + DataTypeUtility._test_valueToString(b) + " result: " + compare);
                }
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
            return keywords.get(sourceOfRandomness.nextInt(0, keywords.size() - 1)).replaceAll("[^A-Za-z0-9]", "");
        }
        else
        {
            // These should be escaped, but would be blown away on load: "\n", "\r", "\t"
            String trimmed = GrammarUtility.collapseSpaces("" + sourceOfRandomness.nextChar('a', 'z') + TestUtil.<@NonNull String>makeList(sourceOfRandomness, 1, 10, () -> sourceOfRandomness.<@NonNull String>choose(Arrays.asList(
                "a", "r", "n", " ", "Z", "0", "9"
            ))).stream().collect(Collectors.joining()));
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
            /*
            if (r.nextInt(5) == 1)
            {
                // Generate awkward escapes:
                int numItems = r.nextInt(20);
                StringBuilder s = new StringBuilder();
                for (int i = 0; i < numItems; i++)
                {
                    s.append(r.choose(ImmutableList.of(
                        "^q", "q", "\"", "^c", "c", "^", "\n", "^n", "@", "\r", "\u0000", " "
                    )));
                }
                return s.toString();
            }
            else
                */
            {
                return new GenString().generate(r, gs);
            }
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
            // TODO add more higher-order types
            TypeManager typeManager = DummyManager.INSTANCE.getTypeManager();
            DataType a = typeManager.registerTaggedType("A", ImmutableList.of(), ImmutableList.of(new TagType<JellyType>("Single", null))).instantiate(ImmutableList.of(), typeManager);
            DataType c = typeManager.registerTaggedType("C", ImmutableList.of(), ImmutableList.of(new TagType<JellyType>("Blank", null), new TagType<JellyType>("Number", JellyType.fromConcrete(DataType.NUMBER)))).instantiate(ImmutableList.of(), typeManager);
            DataType b = typeManager.registerTaggedType("B", ImmutableList.of(), ImmutableList.of(new TagType<JellyType>("Single", null))).instantiate(ImmutableList.of(), typeManager);
            DataType nested = typeManager.registerTaggedType("Nested", ImmutableList.of(), ImmutableList.of(new TagType<JellyType>("A", JellyType.tagged(new TypeId("A"), ImmutableList.of())), new TagType<JellyType>("C", JellyType.tagged(new TypeId("C"), ImmutableList.of())))).instantiate(ImmutableList.of(), typeManager);
            DataType maybeMaybe = typeManager.getMaybeType().instantiate(ImmutableList.of(Either.right(
                typeManager.getMaybeType().instantiate(ImmutableList.of(Either.right(DataType.TEXT)), typeManager)
            )), typeManager);
            distinctTypes = Arrays.<DataType>asList(
                DataType.BOOLEAN,
                DataType.TEXT,
                DataType.date(new DateTimeInfo(DateTimeType.YEARMONTHDAY)),
                DataType.date(new DateTimeInfo(DateTimeType.YEARMONTH)),
                DataType.date(new DateTimeInfo(DateTimeType.DATETIME)),
                DataType.date(new DateTimeInfo(DateTimeType.DATETIMEZONED)),
                DataType.date(new DateTimeInfo(DateTimeType.TIMEOFDAY)),
                //DataType.date(new DateTimeInfo(DateTimeType.TIMEOFDAYZONED)),
                DataType.NUMBER,
                DataType.number(new NumberInfo(DummyManager.INSTANCE.getUnitManager().loadUse("GBP"))),
                DataType.number(new NumberInfo(DummyManager.INSTANCE.getUnitManager().loadUse("m"))),
                DataType.number(new NumberInfo(DummyManager.INSTANCE.getUnitManager().loadUse("m^2"))),
                DataType.number(new NumberInfo(DummyManager.INSTANCE.getUnitManager().loadUse("m^3/s^3"))),
                DataType.number(new NumberInfo(DummyManager.INSTANCE.getUnitManager().loadUse("cm"))),
                DataType.number(new NumberInfo(DummyManager.INSTANCE.getUnitManager().loadUse("(USD*m)/s^2"))),
                a,
                b,
                c,
                nested,
                maybeMaybe,
                DataType.tuple(Arrays.asList(DataType.NUMBER, DataType.NUMBER)),
                DataType.tuple(Arrays.asList(DataType.BOOLEAN, DataType.TEXT, DataType.date(new DateTimeInfo(DateTimeType.DATETIMEZONED)), c)),
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
                typeManager.registerTaggedType(t.getTaggedTypeName().getRaw(), ImmutableList.of(), Utility.mapListInt(t.getTagTypes(), t2 -> t2.mapInt(JellyType::fromConcrete)));
            }
            return new TypeState(unitManager, typeManager);
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }

    public static @ExpressionIdentifier String generateVarName(SourceOfRandomness r)
    {
        @ExpressionIdentifier String s;
        do
        {
            s = IdentifierUtility.asExpressionIdentifier(generateIdent(r));
        }
        while (s == null);
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
            return WaitForAsyncUtils.asyncFx(action).get(60, TimeUnit.SECONDS);
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
            WaitForAsyncUtils.asyncFx(action::run).get(60, TimeUnit.SECONDS);
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
            public @Nullable Void tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, UserException
            {
                typeManager.registerTaggedType(typeName.getRaw(), ImmutableList.of(), Utility.mapListInt(tags, t -> t.mapInt(JellyType::fromConcrete)));
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
    public static Supplier<MainWindowActions> openDataAsTable(Stage windowToUse, TableManager mgr) throws Exception
    {
        File temp = File.createTempFile("srcdata", "tables");
        temp.deleteOnExit();
        String saved = save(mgr);
        System.out.println("Saving: {{{" + saved + "}}}");
        AtomicReference<MainWindowActions> tableManagerAtomicReference = new AtomicReference<>();
        FXUtility.runFX(() -> checkedToRuntime_(() -> {
            MainWindowActions mainWindowActions = MainWindow.show(windowToUse, temp, new Pair<>(temp, saved));
            tableManagerAtomicReference.set(mainWindowActions);
        }));
        // Wait until individual tables are actually loaded:
        return () -> {
            int count = 0;
            do
            {
                //System.err.println("Waiting for main window");
                sleep(1000);
                count += 1;
            }
            while (fx(() -> windowToUse.getScene().lookup(".virt-grid-line")) == null && count < 30);
            if (count >= 30)
                throw new RuntimeException("Could not load table data");
            return tableManagerAtomicReference.get();
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
    public static MainWindowActions openDataAsTable(Stage windowToUse, @Nullable TypeManager typeManager, RecordSet data) throws Exception
    {
        TableManager manager = new DummyManager();
        Table t = new ImmediateDataSource(manager, new InitialLoadDetails(new TableId("Table1"), CellPosition.ORIGIN, null), new EditableRecordSet(data));
        manager.record(t);
        if (typeManager != null)
        {
            manager.getTypeManager()._test_copyTaggedTypesFrom(typeManager);
        }
        return openDataAsTable(windowToUse, manager).get();
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
    
    public static ErrorAndTypeRecorder excOnError()
    {
        return new ErrorAndTypeRecorder()
        {
            @Override
            public <E> void recordError(E src, StyledString error)
            {
                throw new RuntimeException(error.toPlain());
            }

            @Override
            public <EXPRESSION extends StyledShowable, SEMANTIC_PARENT> void recordQuickFixes(EXPRESSION src, List<QuickFix<EXPRESSION, SEMANTIC_PARENT>> quickFixes)
            {
            }

            @SuppressWarnings("recorded")
            @Override
            public @Recorded @NonNull TypeExp recordTypeNN(Expression expression, @NonNull TypeExp typeExp)
            {
                return typeExp;
            }
        };
    }

    @OnThread(Tag.Simulation)
    public static @Nullable Pair<ValueFunction, DataType> typeCheckFunction(FunctionDefinition function, DataType paramType) throws InternalException, UserException
    {
        return typeCheckFunction(function, paramType, null);
    }

    // Returns the function and the return type of the function
    @OnThread(Tag.Simulation)
    public static @Nullable Pair<ValueFunction,DataType> typeCheckFunction(FunctionDefinition function, DataType paramType, @Nullable TypeManager overrideTypeManager) throws InternalException, UserException
    {
        ErrorAndTypeRecorder onError = excOnError();
        TypeManager typeManager = overrideTypeManager != null ? overrideTypeManager : DummyManager.INSTANCE.getTypeManager();
        Pair<TypeExp, Map<String, Either<MutUnitVar, MutVar>>> functionType = function.getType(typeManager);
        MutVar returnTypeVar = new MutVar(null);
        @SuppressWarnings("nullness") // For null src
        TypeExp paramTypeExp = onError.recordError(null, TypeExp.unifyTypes(TypeCons.function(null, TypeExp.fromDataType(null, paramType), returnTypeVar), functionType.getFirst()));
        if (paramTypeExp == null)
            return null;
        
        @SuppressWarnings("nullness") // For null src
        @Nullable DataType returnType = onError.recordLeftError(typeManager, null, returnTypeVar.toConcreteType(typeManager));
        if (returnType != null)
            return new Pair<>(function.getInstance(typeManager, s -> getConcrete(s, functionType.getSecond(), typeManager)), returnType);
        return null;
    }

    private static Either<Unit, DataType> getConcrete(String s, Map<String, Either<MutUnitVar, MutVar>> vars, TypeManager typeManager) throws InternalException, UserException
    {
        Either<MutUnitVar, MutVar> var = vars.get(s);
        if (var == null)
            throw new InternalException("Var " + s + " not found");
        return var.mapBothEx(
            u -> {
                @Nullable Unit concrete = u.toConcreteUnit();
                if (concrete == null)
                    throw new InternalException("Could not concrete unit: " + u);
                return concrete;
            },
            v -> v.toConcreteType(typeManager).getRight(""));
    }

    @OnThread(Tag.Simulation)
    public static @Nullable Pair<ValueFunction,DataType> typeCheckFunction(FunctionDefinition function, DataType expectedReturnType, DataType paramType, @Nullable TypeManager overrideTypeManager) throws InternalException, UserException
    {
        ErrorAndTypeRecorder onError = excOnError();
        TypeManager typeManager = overrideTypeManager != null ? overrideTypeManager : DummyManager.INSTANCE.getTypeManager();
        Pair<TypeExp, Map<String, Either<MutUnitVar, MutVar>>> functionType = function.getType(typeManager);
        MutVar returnTypeVar = new MutVar(null);
        @SuppressWarnings("nullness") // For null src
        @Nullable TypeExp unifiedReturn = onError.recordError(null, TypeExp.unifyTypes(returnTypeVar, TypeExp.fromDataType(null, expectedReturnType)));
        if (null == unifiedReturn)
            return null;
        @SuppressWarnings("nullness") // For null src
        TypeExp funcTypeExp = onError.recordError(null, TypeExp.unifyTypes(TypeCons.function(null, TypeExp.fromDataType(null, paramType), returnTypeVar), functionType.getFirst()));
        if (funcTypeExp == null)
            return null;
            
        @SuppressWarnings("nullness") // For null src
        @Nullable DataType returnType = onError.recordLeftError(typeManager, null, returnTypeVar.toConcreteType(typeManager));
        if (returnType != null)
            return new Pair<>(function.getInstance(typeManager, s -> getConcrete(s, functionType.getSecond(), typeManager)), returnType);
        return null;
    }

    // If null, assertion failure.  Otherwise returns as non-null.
    @SuppressWarnings("nullness")
    public static <T> @NonNull T checkNonNull(@Nullable T t)
    {
        assertNotNull(t);
        return t;
    }

    public @OnThread(Tag.Any)
    static CellPosition tablePosition(TableManager tableManager, TableId srcId) throws UserException
    {
        Table table = tableManager.getSingleTableOrThrow(srcId);
        return checkNonNull(fx(() -> table.getDisplay())).getMostRecentPosition();
    }

    public static KeyCode ctrlCmd()
    {
        return SystemUtils.IS_OS_MAC_OSX ? KeyCode.COMMAND : KeyCode.CONTROL;
    }

    @OnThread(Tag.Simulation)
    public static @Value Object runExpression(String expressionSrc) throws UserException, InternalException
    {
        DummyManager mgr = managerWithTestTypes();
        Expression expression = Expression.parse(null, expressionSrc, mgr.getTypeManager());
        expression.check(r -> null, new TypeState(mgr.getUnitManager(), mgr.getTypeManager()), excOnError());
        return expression.getValue(new EvaluateState(mgr.getTypeManager(), OptionalInt.empty())).getFirst();
    }



    // Used for testing
    // Creates a call to a tag constructor
    @SuppressWarnings("recorded")
    public static Expression tagged(Either<String, TagInfo> constructor, @Nullable Expression arg)
    {
        ConstructorExpression constructorExpression = new ConstructorExpression(constructor);
        if (arg == null)
        {
            return constructorExpression;
        }
        else
        {
            return new CallExpression(constructorExpression, arg);
        }
    }

    public static DummyManager managerWithTestTypes()
    {
        DummyManager mgr;
        try
        {
            mgr = new DummyManager();
            for (DataType type : distinctTypes)
            {
                if (type.isTagged())
                    mgr.getTypeManager().registerTaggedType(type.getTaggedTypeName().getRaw(), ImmutableList.of(),  Utility.mapListInt(type.getTagTypes(), t -> t.mapInt(JellyType::fromConcrete)));
            }
        }
        catch (InternalException | UserException e)
        {
            assumeNoException(e);
            throw new RuntimeException(e);
        }
        return mgr;
    }

    @OnThread(Tag.FXPlatform)
    public static void copySnapshotToClipboard(Node node)
    {
        WritableImage img = node.snapshot(null, null);
        ClipboardContent clipboardContent = new ClipboardContent();
        clipboardContent.putImage(img);
        Clipboard.getSystemClipboard().setContent(clipboardContent);
    }

    @OnThread(Tag.FXPlatform)
    public static void copyScreenshotToClipboard()
    {
        Log.debug("Screenshotting...");
        try
        {
            Robot robot = new Robot();

            Toolkit myToolkit = Toolkit.getDefaultToolkit();
            Dimension screenSize = myToolkit.getScreenSize();

            Rectangle screen = new Rectangle(screenSize);

            BufferedImage screenFullImage = robot.createScreenCapture(screen);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(screenFullImage, "jpg", baos);
            Image image = new Image(new java.io.ByteArrayInputStream(baos.toByteArray()), screenFullImage.getWidth(), screenFullImage.getHeight(), true, true);
            ClipboardContent clipboardContent = new ClipboardContent();
            clipboardContent.putImage(image);
            Clipboard.getSystemClipboard().setContent(clipboardContent);
        }
        catch (AWTException | IOException ex)
        {
            Log.log(ex);
        }        
    }

    public static interface FXPlatformSupplierEx<T> extends Callable<T>
    {
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        public T call() throws InternalException, UserException;
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
