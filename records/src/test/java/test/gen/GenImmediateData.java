package test.gen;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import records.data.Column;
import records.data.ImmediateDataSource;
import records.data.KnownLengthRecordSet;
import records.data.RecordSet;
import records.data.TableManager;
import records.error.FunctionInt;
import records.error.InternalException;
import records.error.UserException;
import test.DummyManager;
import test.gen.GenImmediateData.ImmediateData_Mgr;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Created by neil on 07/12/2016.
 */
public class GenImmediateData extends Generator<ImmediateData_Mgr>
{
    public static class ImmediateData_Mgr
    {
        public final TableManager mgr;
        public final ImmediateDataSource data;

        public ImmediateData_Mgr(TableManager mgr, ImmediateDataSource data)
        {
            this.mgr = mgr;
            this.data = data;
        }
    }

    public GenImmediateData()
    {
        super(ImmediateData_Mgr.class);
    }

    @Override
    @OnThread(value = Tag.Simulation,ignoreParent = true)
    public ImmediateData_Mgr generate(SourceOfRandomness r, GenerationStatus generationStatus)
    {
        try
        {
            TableManager mgr = new GenTableManager().generate(r, generationStatus);

            // Bias towards small:
            final int length = r.nextBoolean() ? r.nextInt(0, 10) : r.nextInt(0, 1111);

            int numColumns = r.nextInt(1, 12);
            List<FunctionInt<RecordSet, Column>> columns = new ArrayList<>();
            GenColumn genColumn = new GenColumn(mgr);
            for (int i = 0; i < numColumns; i++)
            {
                BiFunction<Integer, RecordSet, Column> col = genColumn.generate(r, generationStatus);
                columns.add(rs -> col.apply(length, rs));
            }

            return new ImmediateData_Mgr(mgr, new ImmediateDataSource(mgr, new KnownLengthRecordSet("Title", columns, length)));
        }
        catch (InternalException | UserException e)
        {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    @Override
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public List<ImmediateData_Mgr> doShrink(SourceOfRandomness random, ImmediateData_Mgr larger)
    {
        try
        {
            TableManager mgr = new DummyManager();
            // Don't shrink to zero, gets weird:
            int shrunkLength = Math.max(1, larger.data.getData().getLength() / 4);
            List<FunctionInt<RecordSet, Column>> columns = new ArrayList<>();
            for (Column column : larger.data.getData().getColumns())
            {
                columns.add(rs -> column.shrink(rs, shrunkLength));
            }
            //Could also remove arbitrary column(s)
            return Collections.singletonList(new ImmediateData_Mgr(mgr, new ImmediateDataSource(DummyManager.INSTANCE, new KnownLengthRecordSet(larger.data.getData().getTitle(), columns, shrunkLength))));
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return super.doShrink(random, larger);
        }
    }
}
