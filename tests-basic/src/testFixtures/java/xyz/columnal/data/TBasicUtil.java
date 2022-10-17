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

package xyz.columnal.data;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.generator.java.time.LocalTimeGenerator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.Assert;
import test.gen.GenString;
import test.gen.GenZoneId;
import test.gen.GenNumber;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.DataTypeValue;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.InvalidImmediateValueException;
import xyz.columnal.error.UserException;
import xyz.columnal.grammar.FormatLexer;
import xyz.columnal.grammar.GrammarUtility;
import xyz.columnal.grammar.MainLexer;
import xyz.columnal.id.ColumnId;
import xyz.columnal.id.TableId;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.ExRunnable;
import xyz.columnal.utility.function.ExSupplier;
import xyz.columnal.utility.IdentifierUtility;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.function.simulation.SimulationEx;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Workers;
import xyz.columnal.utility.Workers.Priority;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

@OnThread(Tag.Any)
public class TBasicUtil
{
    @OnThread(Tag.Any)
    public static void assertValueEitherEqual(String prefix, @Nullable Either<String, @Value Object> a, @Nullable Either<String, @Value Object> b) throws UserException, InternalException
    {
        if (a == null && b == null)
            return;
        
        if (a == null || b == null)
        {
            Assert.fail(prefix + " Expected " + a + " but found " + b);
            return;
        }
        @NonNull Either<String, @Value Object> aNN = a;
        @NonNull Either<String, @Value Object> bNN = b;
        
        aNN.either_(aErr -> bNN.either_(
            bErr -> {
                Assert.assertEquals(aErr, bErr);},
            bVal -> {
                Assert.fail(prefix + " Expected Left:" + aErr + " but found Right:" + bVal);}),
            aVal -> bNN.either_(
                bErr -> {
                    Assert.fail(prefix + " Expected Right:" + aVal + " but found Left:" + bErr);},
                bVal -> {assertValueEqual(prefix, aVal, bVal);}
        ));
    }

    @OnThread(Tag.Any)
    public static void assertValueEqual(String prefix, @Nullable @Value Object a, @Nullable @Value Object b)
    {
        try
        {
            if ((a == null) != (b == null))
                Assert.fail(prefix + " differing nullness: " + (a == null) + " vs " + (b == null));
            
            if (a != null && b != null)
            {
                @NonNull @Value Object aNN = a;
                @NonNull @Value Object bNN = b;
                CompletableFuture<Either<Throwable,  Integer>> f = new CompletableFuture<>();
                Workers.onWorkerThread("Comparison", Priority.FETCH, () -> f.complete(exceptionToEither(() -> Utility.compareValues(aNN, bNN))));
                int compare = f.get().either(e -> {throw new RuntimeException(e);}, x -> x);
                if (compare != 0)
                {
                    Assert.fail(prefix + " comparing " + DataTypeUtility._test_valueToString(a) + " against " + DataTypeUtility._test_valueToString(b) + " result: " + compare);
                }
            }
        }
        catch (ExecutionException | InterruptedException e)
        {
            throw new RuntimeException(e);
        }
    }

    @OnThread(Tag.Any)
    public static void assertValueListEqual(String prefix, List<@Value Object> a, @Nullable List<@Value Object> b) throws UserException, InternalException
    {
        Assert.assertNotNull(prefix + " not null", b);
        if (b != null)
        {
            Assert.assertEquals(prefix + " list size", a.size(), b.size());
            for (int i = 0; i < a.size(); i++)
            {
                assertValueEqual(prefix, a.get(i), b.get(i));
            }
        }
    }

    @OnThread(Tag.Any)
    public static void assertValueListEitherEqual(String prefix, List<Either<String, @Value Object>> a, @Nullable List<Either<String, @Value Object>> b) throws UserException, InternalException
    {
        Assert.assertNotNull(prefix + " not null", b);
        if (b != null)
        {
            Assert.assertEquals(prefix + " list size", a.size(), b.size());
            for (int i = 0; i < a.size(); i++)
            {
                assertValueEitherEqual(prefix + " row " + i, a.get(i), b.get(i));
            }
        }
    }

    public static <T> Either<Throwable, T> exceptionToEither(ExSupplier<T> supplier)
    {
        try
        {
            return Either.right(supplier.get());
        }
        catch (Throwable e)
        {
            return Either.left(e);
        }
    }

    @OnThread(Tag.Simulation)
    public static @Value Number generateNumberV(SourceOfRandomness r, GenerationStatus gs)
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
            return DataTypeUtility.valueDate(LocalDate.ofEpochDay(r.nextLong(MIN_CLOSE_DATE.toEpochDay(), MAX_CLOSE_DATE.toEpochDay())));
        else
            return DataTypeUtility.valueDate(LocalDate.ofEpochDay(r.nextLong(MIN_DATE.toEpochDay(), MAX_DATE.toEpochDay())));
    }

    @OnThread(Tag.Simulation)
    public static @Value LocalTime generateTime(SourceOfRandomness r, GenerationStatus gs)
    {
        LocalTime localTime = (LocalTime) DataTypeUtility.valueTime(new LocalTimeGenerator().generate(r, gs));
        // Produce half the dates without partial seconds (and perhaps seconds set to zero): common case
        if (r.nextBoolean())
        {
            localTime = localTime.minusNanos(localTime.getNano());
            if (r.nextBoolean())
                localTime = localTime.minusSeconds(localTime.getSecond());
        }
        @SuppressWarnings("valuetype")
        @Value LocalTime ret = localTime;
        return ret;
    }

    @OnThread(Tag.Simulation)
    public static @Value LocalDateTime generateDateTime(SourceOfRandomness r, GenerationStatus gs)
    {
        return LocalDateTime.of(generateDate(r, gs), generateTime(r, gs));
    }

    @OnThread(Tag.Simulation)
    public static @Value ZonedDateTime generateDateTimeZoned(SourceOfRandomness r, GenerationStatus gs)
    {
        return DataTypeUtility.valueZonedDateTime(ZonedDateTime.of(generateDateTime(r, gs), r.nextBoolean() ? generateZone(r, gs) : generateZoneOffset(r, gs)));
    }

    @OnThread(Tag.Simulation)
    public static ZoneId generateZone(SourceOfRandomness r, GenerationStatus gs)
    {
        return new GenZoneId().generate(r, gs);
    }

    public static ZoneOffset generateZoneOffset(SourceOfRandomness r, GenerationStatus gs)
    {
        // We don't support sub-minute offsets:
        return ZoneOffset.ofTotalSeconds(r.nextInt(-12*60, 12*60) * 60);
    }

    @OnThread(Tag.Simulation)
    public static String generateZoneString(SourceOfRandomness r, GenerationStatus gs)
    {
        return generateZone(r, gs).toString();
    }

    @SuppressWarnings("identifier")
    public static @ExpressionIdentifier String generateExpressionIdentifier(SourceOfRandomness sourceOfRandomness)
    {
        return IdentifierUtility.fixExpressionIdentifier(generateIdent(sourceOfRandomness), "Exp " + sourceOfRandomness.nextInt(1000000));
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

        for (int i = 0; i < FormatLexer.VOCABULARY.getMaxTokenType(); i++)
        {
            String l = FormatLexer.VOCABULARY.getLiteralName(i);
            if (l != null)
            {
                keywords.add(l);
            }
        }
        return keywords;
    }

    public static String generateIdent(SourceOfRandomness sourceOfRandomness)
    {
        if (sourceOfRandomness.nextBoolean())
        {
            List<String> keywords = getKeywords();
            String mangled = keywords.get(sourceOfRandomness.nextInt(0, keywords.size() - 1)).replaceAll("[^A-Za-z0-9]", "").trim();
            if (mangled.isEmpty())
                return "a";
            else
                return mangled;
        }
        else
        {
            // These should be escaped, but would be blown away on load: "\n", "\r", "\t"
            String trimmed = GrammarUtility.collapseSpaces("" + sourceOfRandomness.nextChar('a', 'z') + TBasicUtil.<@NonNull String>makeList(sourceOfRandomness, 1, 10, () -> sourceOfRandomness.<@NonNull String>choose(Arrays.<String>asList(
                    "a", "r", "n", " ", "Z", "0", "9"
            ))).stream().collect(Collectors.joining()));
            if (trimmed.isEmpty())
                return "a";
            else
                return trimmed;
        }
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

    @OnThread(Tag.Simulation)
    public static @Value String makeStringV(SourceOfRandomness r, GenerationStatus gs)
    {
        return DataTypeUtility.value(makeString(r, gs).replaceAll("\"", ""));
    }

    @OnThread(Tag.Simulation)
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
                String s = new GenString().generate(r, gs);
                if (s.length() > 250)
                    s = s.substring(0, 250);
                return s;
            }
        }
        else
            return generateIdent(r);
    }

    @OnThread(Tag.Simulation)
    public static List<Either<String, @Value Object>> getAllCollapsedData(DataTypeValue type, int size) throws UserException, InternalException
    {
        List<Either<String, @Value Object>> r = new ArrayList<>();
        for (int i = 0; i < size; i++)
        {
            try
            {
                r.add(Either.right(type.getCollapsed(i)));
            }
            catch (InvalidImmediateValueException e)
            {
                r.add(Either.left(e.getInvalid()));
            }
        }
        return r;
    }

    @OnThread(Tag.Simulation)
    public static List<@Value Object> getAllCollapsedDataValid(DataTypeValue type, int size) throws UserException, InternalException
    {
        List<@Value Object> r = new ArrayList<>();
        for (int i = 0; i < size; i++)
        {
            r.add(type.getCollapsed(i));
        }
        return r;
    }

    @OnThread(Tag.Simulation)
    public static void assertUserException(SimulationEx simulationRunnable)
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

    @OnThread(Tag.Simulation)
    public static <T> ImmutableList<T> makeList(int len, Generator<? extends T> gen, SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
    {
        return Stream.generate(() -> gen.generate(sourceOfRandomness, generationStatus)).limit(len).collect(ImmutableList.toImmutableList());
    }

    public static ColumnId generateColumnId(SourceOfRandomness sourceOfRandomness)
    {
        return new ColumnId(IdentifierUtility.fixExpressionIdentifier(generateIdent(sourceOfRandomness), "Column"));
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
        return new TableId(IdentifierUtility.fixExpressionIdentifier(generateIdent(sourceOfRandomness), "Table"));
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

    @OnThread(Tag.Simulation)
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

    // If null, assertion failure.  Otherwise returns as non-null.
    @SuppressWarnings("nullness")
    public static <T> @NonNull T checkNonNull(@Nullable T t)
    {
        assertNotNull(t);
        return t;
    }

    // Needed until IntelliJ bug IDEA-198613 is fixed
    public static void printSeedOnFail(TestRunnable r) throws Exception
    {
        try
        {
            r.run();
        }
        catch (AssertionError assertionError)
        {
            String message = assertionError.getMessage();
            message = message == null ? "" :
                    message.replace("expected:", "should be").replace("but was:", "alas found");
            throw new AssertionError(message, assertionError);
        }
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

    @OnThread(Tag.Any)
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

    /**
     * Removes a random item from the given list and returns it.
     * Note that the list is modified!
     */
    public static <T> T removeRandom(Random r, List<T> list)
    {
        int index = r.nextInt(list.size());
        return list.remove(index);
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
                assertEquals(prefix + "\nExpected line " + i + ": " + (expectedLine == null ? "null" : stringAsHexChars(expectedLine) + "\n" + expectedLine) + "\nActual: " + (actualLine == null ? "null" : stringAsHexChars(actualLine) + "\n" + actualLine), expectedLine, actualLine);
            }
        }
    }

    public static String stringAsHexChars(String str)
    {
        return str.chars().mapToObj(c -> Integer.toHexString(c) + (c == 10 ? "\n" : "")).collect(Collectors.joining(" "));
    }

    @OnThread(Tag.Simulation)
    public static Either<String, @Value Object> getSingleCollapsedData(DataTypeValue type, int index) throws UserException, InternalException
    {
        try
        {
            return Either.right(type.getCollapsed(index));
        }
        catch (InvalidImmediateValueException e)
        {
            return Either.left(e.getInvalid());
        }
    }

    public static interface TestRunnable
    {
        public void run() throws Exception;
    }
}
