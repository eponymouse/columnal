package test.gen;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.generator.java.lang.StringGenerator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import records.data.Column;
import records.data.ColumnId;
import records.data.MemoryStringColumn;
import records.data.RecordSet;
import records.error.InternalException;
import test.DummyManager;
import test.TestUtil;
import threadchecker.OnThread;
import threadchecker.Tag;

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
            }
        ));
    }
}
