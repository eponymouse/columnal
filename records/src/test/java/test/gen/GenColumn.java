package test.gen;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.generator.java.lang.StringGenerator;
import com.pholser.junit.quickcheck.generator.java.time.LocalDateGenerator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import records.data.Column;
import records.data.ColumnId;
import records.data.MemoryNumericColumn;
import records.data.MemoryStringColumn;
import records.data.MemoryTemporalColumn;
import records.data.RecordSet;
import records.data.columntype.NumericColumnType;
import records.error.InternalException;
import records.error.UserException;
import test.DummyManager;
import test.TestUtil;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.math.BigDecimal;
import java.time.temporal.Temporal;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Created by neil on 02/12/2016.
 */
public class GenColumn extends Generator<BiFunction<Integer, RecordSet, Column>>
{
    public GenColumn()
    {
        super((Class<BiFunction<Integer, RecordSet, Column>>)(Class<?>)BiFunction.class);
    }

    @Override
    @OnThread(value = Tag.Simulation,ignoreParent = true)
    public BiFunction<Integer, RecordSet, Column> generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
    {
        Supplier<ColumnId> nextCol = new Supplier<ColumnId>() {
            int nextId = 0;
            @Override
            public ColumnId get()
            {
                return new ColumnId("GenCol" + (nextId++));
            }
        };
        // We choose between string, date, numeric
        //TODO add boolean
        //TODO add tagged test, especially those which fit in single numeric vs not
        return sourceOfRandomness.choose(Arrays.asList(
            (len, rs) -> {
                try
                {
                    StringGenerator stringGenerator = new StringGenerator();
                    return new MemoryStringColumn(rs, nextCol.get(), TestUtil.makeList(len, stringGenerator, sourceOfRandomness, generationStatus));
                }
                catch (InternalException e)
                {
                    throw new RuntimeException(e);
                }
            },
            (len, rs) -> {
                try
                {
                    LocalDateGenerator gen = new LocalDateGenerator();
                    return new MemoryTemporalColumn(rs, nextCol.get(), TestUtil.<Temporal>makeList(len, gen, sourceOfRandomness, generationStatus));
                }
                catch (InternalException e)
                {
                    throw new RuntimeException(e);
                }
            },
            (len, rs) -> {
                try
                {
                    Generator<String> gen;
                    if (sourceOfRandomness.nextBoolean())
                    {
                        // Integer only
                        long limit = sourceOfRandomness.choose(Arrays.asList(0L, 1L, 100L, 10_000L, 1_000_000_000L, 1_000_000_000_000L));
                        gen = new Generator<String>(String.class)
                        {
                            @Override
                            public String generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
                            {
                                return Long.toString(sourceOfRandomness.nextLong(-limit, limit));
                            }
                        };
                    }
                    else
                    {
                        // Big decimals:
                        gen = new Generator<String>(String.class)
                        {
                            @Override
                            public String generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
                            {
                                return new BigDecimal(sourceOfRandomness.nextBigInteger(80), sourceOfRandomness.nextInt(-20, 20)).toPlainString();
                            }
                        };
                    }
                    return new MemoryNumericColumn(rs, nextCol.get(), new NumericColumnType("", 0, false), TestUtil.<String>makeList(len, gen, sourceOfRandomness, generationStatus));
                }
                catch (InternalException | UserException e)
                {
                    throw new RuntimeException(e);
                }
            }
        ));
    }
}
