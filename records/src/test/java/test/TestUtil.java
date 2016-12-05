package test;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.generator.java.lang.StringGenerator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import one.util.streamex.StreamEx;
import one.util.streamex.StreamEx.Emitter;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Column;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.TableId;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.MainLexer;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
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
                "a", "Z", "0", "9", "-", "=", "+", " ", "^", "@", "\"", "'"
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

    public static <T> List<T> makeList(SourceOfRandomness r, int minSizeIncl, int maxSizeIncl, Supplier<T> makeOne)
    {
        int size = r.nextInt(minSizeIncl, maxSizeIncl);
        ArrayList<T> list = new ArrayList<>();
        for (int i = 0; i < size; i++)
            list.add(makeOne.get());
        return list;
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
    public static StreamEx<List<List<Object>>> streamFlattened(RecordSet src)
    {
        return new StreamEx.Emitter<List<List<Object>>>()
        {
            int nextIndex = 0;
            @Override
            @OnThread(value = Tag.Simulation, ignoreParent = true)
            public @Nullable Emitter<List<List<Object>>> next(Consumer<? super List<List<Object>>> consumer)
            {
                try
                {
                    if (src.indexValid(nextIndex))
                    {
                        List<List<Object>> collapsed = src.getColumns().stream().map(c ->
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
    public static StreamEx<List<Object>> streamFlattened(Column column)
    {
        return new StreamEx.Emitter<List<Object>>()
        {
            int nextIndex = 0;
            @Override
            @OnThread(value = Tag.Simulation, ignoreParent = true)
            public @Nullable Emitter<List<Object>> next(Consumer<? super List<Object>> consumer)
            {
                try
                {
                    if (column.indexValid(nextIndex))
                    {
                        List<Object> collapsed = column.getType().getCollapsed(nextIndex);
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
    public static Map<List<List<Object>>, Long> getRowFreq(RecordSet src)
    {
        return streamFlattened(src).collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    }

    public static String makeString(SourceOfRandomness r, GenerationStatus gs)
    {
        // Makes either totally random String from generator, or "awkward" string
        // with things likely to trip up parser
        if (r.nextBoolean())
            return new StringGenerator().generate(r, gs);
        else
            return generateIdent(r);
    }

    public static String makeUnquotedIdent(SourceOfRandomness r, GenerationStatus gs)
    {
        String s;
        do
        {
            s = makeString(r, gs);
        }
        while (!Utility.validUnquoted(s));
        return s;
    }
}
