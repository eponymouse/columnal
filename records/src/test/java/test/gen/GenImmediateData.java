package test.gen;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.generator.java.lang.StringGenerator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import records.data.Column;
import records.data.ColumnId;
import records.data.ImmediateDataSource;
import records.data.KnownLengthRecordSet;
import records.data.MemoryStringColumn;
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
            final int length = r.nextInt(0, 1111);
            List<DataType> types = TestUtil.makeList(r, 1, 10, () -> r.choose(Arrays.asList(
                DataType.NUMBER,
                DataType.BOOLEAN,
                DataType.TEXT,
                DataType.DATE
                // TODO add tagged
            )));

            int numColumns = r.nextInt(1, 12);
            List<ColumnId> colNames = TestUtil.generateColumnIds(r, numColumns);
            List<FunctionInt<RecordSet, Column>> columns = new ArrayList<>();
            for (ColumnId colName : colNames)
            {
                columns.add(r.choose(Arrays.asList(
                    rs ->
                    {
                        return new MemoryStringColumn(rs, colName, TestUtil.makeList(length, new StringGenerator(), r, generationStatus));
                    }
                )));
            }


            return new ImmediateDataSource(DummyManager.INSTANCE, new KnownLengthRecordSet("Title", columns, length));
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }
}
