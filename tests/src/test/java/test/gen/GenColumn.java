package test.gen;

import annotation.qual.Value;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import org.checkerframework.checker.nullness.qual.NonNull;
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
import utility.Either;
import utility.ExBiFunction;
import utility.IdentifierUtility;
import utility.Utility;

import java.util.List;
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
            return new ColumnId(IdentifierUtility.identNum("GenCol", (nextId++)));
        }
    };
    private final List<DataType> distinctTypes;
    private final boolean canHaveErrors;

    @SuppressWarnings("unchecked")
    public GenColumn(TableManager mgr, List<DataType> distinctTypes, boolean canHaveErrors)
    {
        super((Class<ExBiFunction<Integer, RecordSet, Column>>)(Class<?>)BiFunction.class);
        this.mgr = mgr;
        this.distinctTypes = distinctTypes;
        this.canHaveErrors = canHaveErrors;
    }

    @Override
    @OnThread(value = Tag.Simulation,ignoreParent = true)
    public ExBiFunction<Integer, RecordSet, Column> generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
    {
        this.r = sourceOfRandomness;
        this.gs = generationStatus;
        DataType type = sourceOfRandomness.choose(distinctTypes);
        try
        {
            return columnForType(type, sourceOfRandomness);
        }
        catch (InternalException e)
        {
            throw new RuntimeException(e);
        }
    }

    // Only valid to call after generate has been called at least once
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public ExBiFunction<Integer, RecordSet, Column> columnForType(DataType type, SourceOfRandomness sourceOfRandomness) throws InternalException
    {
        return (len, rs) -> type.makeImmediateColumn(nextCol.get(), Utility.<Either<String, @Value Object>>makeListEx(len, i -> {
            if (canHaveErrors && sourceOfRandomness.nextInt(10) == 1)
                return Either.left("#" + r.nextInt());
            else
                return Either.right(makeValue(type));
        }), makeValue(type)).apply(rs);
    }
}
