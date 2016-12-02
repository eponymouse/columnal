package test.gen;

import com.pholser.junit.quickcheck.generator.Gen;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Column;
import records.data.DataSource;
import records.data.ImmediateDataSource;
import records.data.RecordSet;
import records.data.Table;
import records.data.datatype.DataTypeValue;
import records.error.FunctionInt;
import records.error.InternalException;
import records.error.UserException;
import test.DummyManager;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Created by neil on 02/12/2016.
 */
public class GenTable extends Generator<Table>
{
    public GenTable()
    {
        super(Table.class);
    }

    @Override
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public Table generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
    {
        try
        {
            int length = sourceOfRandomness.nextInt(1, 300);
            List<FunctionInt<RecordSet, Column>> columns = new ArrayList<>();
            int numCols = sourceOfRandomness.nextInt(1, 10);
            Generator<BiFunction<Integer, RecordSet, Column>> genColumn = new GenColumn();
            for (int i = 0; i < numCols; i++)
            {
                columns.add(rs -> genColumn.generate(sourceOfRandomness, generationStatus).apply(length, rs));
            }

            RecordSet data = new RecordSet("GenRS", columns)
            {
                @Override
                public boolean indexValid(int index) throws UserException, InternalException
                {
                    return index < length;
                }
            };
            return new ImmediateDataSource(DummyManager.INSTANCE, data);
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }
}
