package xyz.columnal.data;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.java.time.LocalTimeGenerator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.Assert;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.DataTypeValue;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.InvalidImmediateValueException;
import xyz.columnal.error.UserException;
import records.grammar.FormatLexer;
import records.grammar.GrammarUtility;
import records.grammar.MainLexer;
import test.gen.GenNumber;
import test.gen.GenString;
import test.gen.GenZoneId;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Either;
import xyz.columnal.utility.ExSupplier;
import xyz.columnal.utility.IdentifierUtility;
import xyz.columnal.utility.Pair;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class DataTestUtil
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
    public static ImmutableList<@Value Object> getRowVals(RecordSet recordSet, int targetRow)
    {
        return recordSet.getColumns().stream().<@Value Object>map(c -> {
            try
            {
                return c.getType().getCollapsed(targetRow);
            }
            catch (InternalException | UserException e)
            {
                throw new RuntimeException(e);
            }
        }).collect(ImmutableList.<@Value Object>toImmutableList());
    }

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

    public static @Value LocalDateTime generateDateTime(SourceOfRandomness r, GenerationStatus gs)
    {
        return LocalDateTime.of(generateDate(r, gs), generateTime(r, gs));
    }

    public static @Value ZonedDateTime generateDateTimeZoned(SourceOfRandomness r, GenerationStatus gs)
    {
        return DataTypeUtility.valueZonedDateTime(ZonedDateTime.of(generateDateTime(r, gs), r.nextBoolean() ? generateZone(r, gs) : generateZoneOffset(r, gs)));
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
            String trimmed = GrammarUtility.collapseSpaces("" + sourceOfRandomness.nextChar('a', 'z') + DataTestUtil.<@NonNull String>makeList(sourceOfRandomness, 1, 10, () -> sourceOfRandomness.<@NonNull String>choose(Arrays.<String>asList(
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
}
