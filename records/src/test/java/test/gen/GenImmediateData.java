package test.gen;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.generator.java.lang.BooleanGenerator;
import com.pholser.junit.quickcheck.generator.java.lang.StringGenerator;
import com.pholser.junit.quickcheck.generator.java.time.LocalDateGenerator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import records.data.Column;
import records.data.ColumnId;
import records.data.ImmediateDataSource;
import records.data.KnownLengthRecordSet;
import records.data.MemoryBooleanColumn;
import records.data.MemoryStringColumn;
import records.data.MemoryTemporalColumn;
import records.data.RecordSet;
import records.data.datatype.DataType;
import records.error.FunctionInt;
import records.error.InternalException;
import records.error.UserException;
import test.DummyManager;
import test.TestUtil;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Created by neil on 07/12/2016.
 */
public class GenImmediateData extends Generator<ImmediateDataSource>
{
    public GenImmediateData()
    {
        super(ImmediateDataSource.class);
    }

    @Override
    @OnThread(value = Tag.Simulation,ignoreParent = true)
    public ImmediateDataSource generate(SourceOfRandomness r, GenerationStatus generationStatus)
    {
        try
        {
            // Bias towards small:
            final int length = r.nextBoolean() ? r.nextInt(0, 10) : r.nextInt(0, 1111);

            int numColumns = r.nextInt(1, 12);
            List<ColumnId> colNames = TestUtil.generateColumnIds(r, numColumns);
            List<FunctionInt<RecordSet, Column>> columns = new ArrayList<>();
            for (ColumnId colName : colNames)
            {
                columns.add(r.choose(Arrays.asList(
                    rs -> new MemoryStringColumn(rs, colName, TestUtil.makeList(length, new StringGenerator(), r, generationStatus)),
                    rs -> new MemoryTemporalColumn(rs, colName, TestUtil.makeList(length, new LocalDateGenerator(), r, generationStatus)),
                    rs -> new MemoryBooleanColumn(rs, colName, TestUtil.makeList(length, new BooleanGenerator(), r, generationStatus))
                    //TODO numbers, tags
                )));
            }


            return new ImmediateDataSource(DummyManager.INSTANCE, new KnownLengthRecordSet("Title", columns, length));
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public List<ImmediateDataSource> doShrink(SourceOfRandomness random, ImmediateDataSource larger)
    {
        try
        {
            // Don't shrink to zero, gets weird:
            int shrunkLength = Math.max(1, larger.getData().getLength() / 4);
            List<FunctionInt<RecordSet, Column>> columns = new ArrayList<>();
            for (Column column : larger.getData().getColumns())
            {
                columns.add(rs -> column.shrink(rs, shrunkLength));
            }
            //TODO also remove arbitrary column(s)
            return Collections.singletonList(new ImmediateDataSource(DummyManager.INSTANCE, new KnownLengthRecordSet(larger.getData().getTitle(), columns, shrunkLength)));
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return super.doShrink(random, larger);
        }
    }
}
