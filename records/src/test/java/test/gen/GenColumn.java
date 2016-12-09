package test.gen;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.generator.java.lang.BooleanGenerator;
import com.pholser.junit.quickcheck.generator.java.lang.StringGenerator;
import com.pholser.junit.quickcheck.generator.java.time.LocalDateGenerator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Column;
import records.data.ColumnId;
import records.data.MemoryBooleanColumn;
import records.data.MemoryNumericColumn;
import records.data.MemoryStringColumn;
import records.data.MemoryTaggedColumn;
import records.data.MemoryTemporalColumn;
import records.data.RecordSet;
import records.data.columntype.NumericColumnType;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DataTypeVisitor;
import records.data.datatype.DataType.NumberDisplayInfo;
import records.data.datatype.DataType.TagType;
import records.error.InternalException;
import records.error.UserException;
import test.DummyManager;
import test.TestUtil;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

import java.math.BigDecimal;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Created by neil on 02/12/2016.
 */
public class GenColumn extends Generator<BiFunction<Integer, RecordSet, Column>>
{
    private Supplier<ColumnId> nextCol = new Supplier<ColumnId>() {
        int nextId = 0;
        @Override
        public ColumnId get()
        {
            return new ColumnId("GenCol" + (nextId++));
        }
    };

    public GenColumn()
    {
        super((Class<BiFunction<Integer, RecordSet, Column>>)(Class<?>)BiFunction.class);
    }

    @Override
    @OnThread(value = Tag.Simulation,ignoreParent = true)
    public BiFunction<Integer, RecordSet, Column> generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
    {

        // We choose between string, date, numeric
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

                    Generator<? extends Boolean> boolGenerator = new BooleanGenerator();
                    return new MemoryBooleanColumn(rs, nextCol.get(), TestUtil.makeList(len, boolGenerator, sourceOfRandomness, generationStatus));
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
            },
            (len, rs) -> {
                try
                {
                    List<TagType<DataType>> tags = makeTags(0, sourceOfRandomness, generationStatus);
                    return new MemoryTaggedColumn(rs, nextCol.get(), tags, TestUtil.makeList(len, new TagDataGenerator(tags), sourceOfRandomness, generationStatus));
                }
                catch (InternalException | UserException e)
                {
                    throw new RuntimeException(e);
                }
            }
        ));
    }

    private static List<TagType<DataType>> makeTags(int depth, final SourceOfRandomness sourceOfRandomness, final GenerationStatus generationStatus)
    {
        return TestUtil.makeList(sourceOfRandomness, 1, 10, new Supplier<TagType<DataType>>()
        {
            Set<String> usedNames = new HashSet<>();
            @Override
            public TagType<DataType> get()
            {
                String name;
                do
                {
                    name = TestUtil.makeString(sourceOfRandomness, generationStatus);
                }
                while (usedNames.contains(name));
                usedNames.add(name);
                return new TagType<DataType>(name, sourceOfRandomness.choose(Arrays.<Supplier<@Nullable DataType>>asList(
                    () -> null,
                    () -> DataType.TEXT,
                    () -> DataType.NUMBER,
                    () -> DataType.BOOLEAN,
                    () -> DataType.DATE,
                    () -> depth < 3 ? DataType.tagged(makeTags(depth + 1, sourceOfRandomness, generationStatus)) : null
                )).get());
            }
        });
    }

    private static class TagDataGenerator extends Generator<List<Object>>
    {
        private final List<TagType<DataType>> tags;

        public TagDataGenerator(List<TagType<DataType>> tags)
        {
            super((Class<List<Object>>)(Class<?>)List.class);
            this.tags = tags;
        }

        @Override
        public List<Object> generate(SourceOfRandomness r, GenerationStatus generationStatus)
        {
            int tagIndex = r.nextInt(0, tags.size() - 1);
            TagType<DataType> tag = tags.get(tagIndex);
            @Nullable DataType inner = tag.getInner();
            if (inner == null)
                return Collections.singletonList((Integer)tagIndex);
            else
            {
                try
                {
                    List<Object> o = new ArrayList<>();
                    o.add((Integer) tagIndex);
                    o.addAll(inner.apply(new DataTypeVisitor<List<Object>>()
                    {

                        @Override
                        public List<Object> number(NumberDisplayInfo displayInfo) throws InternalException, UserException
                        {
                            return Collections.singletonList(Utility.parseNumber(new GenNumber().generate(r, generationStatus)));
                        }

                        @Override
                        public List<Object> text() throws InternalException, UserException
                        {
                            return Collections.singletonList(TestUtil.makeString(r, generationStatus));
                        }

                        @Override
                        public List<Object> date() throws InternalException, UserException
                        {
                            return Collections.singletonList(new LocalDateGenerator().generate(r, generationStatus));
                        }

                        @Override
                        public List<Object> bool() throws InternalException, UserException
                        {
                            return Collections.singletonList(r.nextBoolean());
                        }

                        @Override
                        public List<Object> tagged(List<TagType<DataType>> tags) throws InternalException, UserException
                        {
                            return new TagDataGenerator(tags).generate(r, generationStatus);
                        }
                    }));
                    return o;
                }
                catch (InternalException | UserException e)
                {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
