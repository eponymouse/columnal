package test.gen;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.generator.java.lang.BooleanGenerator;
import com.pholser.junit.quickcheck.generator.java.lang.StringGenerator;
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
import records.data.TableManager;
import records.data.TaggedValue;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DataTypeVisitor;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.datatype.DataType.NumberInfo;
import records.data.datatype.DataType.TagType;
import records.data.datatype.TypeId;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UnimplementedException;
import records.error.UserException;
import test.TestUtil;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExSupplier;
import utility.Pair;

import java.math.BigDecimal;
import java.time.temporal.TemporalAccessor;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Created by neil on 02/12/2016.
 */
public class GenColumn extends Generator<BiFunction<Integer, RecordSet, Column>>
{
    private final TableManager mgr;
    private Supplier<ColumnId> nextCol = new Supplier<ColumnId>() {
        int nextId = 0;
        @Override
        public ColumnId get()
        {
            return new ColumnId("GenCol" + (nextId++));
        }
    };

    public GenColumn(TableManager mgr)
    {
        super((Class<BiFunction<Integer, RecordSet, Column>>)(Class<?>)BiFunction.class);
        this.mgr = mgr;
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
                    return new MemoryTemporalColumn(rs, nextCol.get(), new DateTimeInfo(DateTimeType.YEARMONTHDAY), TestUtil.<TemporalAccessor>makeList(sourceOfRandomness, len, len, () -> TestUtil.generateDate(sourceOfRandomness, generationStatus)));
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
                    return new MemoryNumericColumn(rs, nextCol.get(), new NumberInfo(Unit.SCALAR, 0), TestUtil.<String>makeList(len, gen, sourceOfRandomness, generationStatus).stream());
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
                    DataType type = mgr.getTypeManager().registerTaggedType(TestUtil.makeNonEmptyString(sourceOfRandomness, generationStatus), tags);
                    return new MemoryTaggedColumn(rs, nextCol.get(), type.getTaggedTypeName(), tags, TestUtil.makeList(len, new TagDataGenerator(tags), sourceOfRandomness, generationStatus));
                }
                catch (InternalException e)
                {
                    throw new RuntimeException(e);
                }
            }
        ));
    }

    private List<TagType<DataType>> makeTags(int depth, final SourceOfRandomness sourceOfRandomness, final GenerationStatus generationStatus)
    {
        return TestUtil.makeList(sourceOfRandomness, 1, 10, new ExSupplier<TagType<DataType>>()
        {
            Set<String> usedNames = new HashSet<>();
            @Override
            public TagType<DataType> get()
            {
                String name;
                do
                {
                    name = TestUtil.makeNonEmptyString(sourceOfRandomness, generationStatus);
                }
                while (usedNames.contains(name));
                usedNames.add(name);
                return new TagType<DataType>(name, sourceOfRandomness.choose(Arrays.<Supplier<@Nullable DataType>>asList(
                    () -> null,
                    () -> DataType.TEXT,
                    () -> DataType.NUMBER,
                    () -> DataType.BOOLEAN,
                    () -> DataType.DATE,
                    () ->
                    {
                        try
                        {
                            return depth < 3 ? mgr.getTypeManager().registerTaggedType(TestUtil.makeString(sourceOfRandomness, generationStatus), makeTags(depth + 1, sourceOfRandomness, generationStatus)) : null;
                        }
                        catch (InternalException e)
                        {
                            throw new RuntimeException(e);
                        }
                    }
                )).get());
            }
        });
    }

    private static class TagDataGenerator extends Generator<TaggedValue>
    {
        private final List<TagType<DataType>> tags;

        public TagDataGenerator(List<TagType<DataType>> tags)
        {
            super(TaggedValue.class);
            this.tags = tags;
        }

        @Override
        public TaggedValue generate(SourceOfRandomness r, GenerationStatus generationStatus)
        {
            int tagIndex = r.nextInt(0, tags.size() - 1);
            TagType<DataType> tag = tags.get(tagIndex);
            @Nullable DataType inner = tag.getInner();
            if (inner == null)
                return new TaggedValue(tagIndex, null);
            else
            {
                try
                {
                    Object value = inner.<Object, UserException>apply(new DataTypeVisitor<Object>()
                    {

                        @Override
                        public Object number(NumberInfo displayInfo) throws InternalException, UserException
                        {
                            return TestUtil.generateNumber(r, generationStatus);
                        }

                        @Override
                        public Object text() throws InternalException, UserException
                        {
                            return TestUtil.makeString(r, generationStatus);
                        }

                        @Override
                        public Object date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
                        {
                            return TestUtil.generateDate(r, generationStatus);
                        }

                        @Override
                        public Object bool() throws InternalException, UserException
                        {
                            return r.nextBoolean();
                        }

                        @Override
                        public Object tagged(TypeId typeName, List<TagType<DataType>> tags) throws InternalException, UserException
                        {
                            return new TagDataGenerator(tags).generate(r, generationStatus);
                        }

                        @Override
                        public Object tuple(List<DataType> inner) throws InternalException, UserException
                        {
                            throw new UnimplementedException();
                        }

                        @Override
                        public Object array(DataType inner) throws InternalException, UserException
                        {
                            throw new UnimplementedException();
                        }
                    });
                    return new TaggedValue((Integer) tagIndex, value);
                }
                catch (InternalException | UserException e)
                {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
