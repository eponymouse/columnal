package test.gen;

import annotation.qual.Value;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import org.jetbrains.annotations.NotNull;
import records.data.Column;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.TableManager;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;
import test.TestUtil;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExBiFunction;
import utility.Utility;

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

    @SuppressWarnings("unchecked")
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
        try
        {
            TestUtil.registerAllTaggedTypes(mgr.getTypeManager(), type);
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
        return columnForType(type);
    }

    // Only valid to call after generate has been called at least once
    @NotNull
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public ExBiFunction<Integer, RecordSet, Column> columnForType(DataType type)
    {
        return (len, rs) -> type.makeImmediateColumn(nextCol.get(), Utility.<@Value Object>makeListEx(len, i -> makeValue(type)), makeValue(type)).apply(rs);
    }
}
