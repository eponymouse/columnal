package test.gen;

import annotation.qual.Value;
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
import utility.ExBiFunction;
import utility.ExSupplier;
import utility.Pair;
import utility.Utility;

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
public class GenColumn extends GenValueBase<ExBiFunction<Integer, RecordSet, Column>>
{
    private final TableManager mgr;
    // Static so we don't get unintended duplicates during testing:
    private static Supplier<ColumnId> nextCol = new Supplier<ColumnId>() {
        int nextId = 0;
        @Override
        public ColumnId get()
        {
            return new ColumnId("GenCol" + (nextId++));
        }
    };

    public GenColumn(TableManager mgr)
    {
        super((Class<ExBiFunction<Integer, RecordSet, Column>>)(Class<?>)BiFunction.class);
        this.mgr = mgr;
    }

    @Override
    @OnThread(value = Tag.Simulation,ignoreParent = true)
    public ExBiFunction<Integer, RecordSet, Column> generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
    {
        this.r = sourceOfRandomness;
        this.gs = generationStatus;
        DataType type = sourceOfRandomness.choose(TestUtil.distinctTypes);
        for (DataType taggedType : TestUtil.distinctTypes)
        {
            if (!taggedType.isTagged())
                continue;

            try
            {
                mgr.getTypeManager().registerTaggedType(taggedType.getTaggedTypeName().getRaw(), taggedType.getTagTypes());
            }
            catch (InternalException e)
            {
                throw new RuntimeException(e);
            }
        }
        return (len, rs) -> type.makeCalculatedColumn(rs, nextCol.get(), i -> makeValue(type));
    }
}
