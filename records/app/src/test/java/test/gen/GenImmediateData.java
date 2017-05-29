package test.gen;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.generator.Precision;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import records.data.Column;
import records.data.EditableRecordSet;
import records.data.ImmediateDataSource;
import records.data.KnownLengthRecordSet;
import records.data.RecordSet;
import records.data.Table;
import records.data.TableManager;
import records.error.FunctionInt;
import records.error.InternalException;
import records.error.UserException;
import test.gen.GenImmediateData.ImmediateData_Mgr;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExBiFunction;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by neil on 07/12/2016.
 */
public class GenImmediateData extends Generator<ImmediateData_Mgr>
{
    public static class ImmediateData_Mgr
    {
        public final TableManager mgr;
        // Amount is set by Precision annotation (for laziness), default is 1
        public final List<ImmediateDataSource> data;

        public ImmediateData_Mgr(TableManager mgr, List<ImmediateDataSource> data)
        {
            this.mgr = mgr;
            this.data = new ArrayList<>(data);
        }

        // Shortcut for first item:
        public Table data()
        {
            return data.get(0);
        }
    }

    private int numTables = 1;

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

            List<ImmediateDataSource> tables = new ArrayList<>();

            for (int t = 0; t < numTables; t++)
            {
                // Bias towards small:
                final int length = r.nextBoolean() ? r.nextInt(0, 10) : r.nextInt(0, 1111);

                int numColumns = r.nextInt(1, 12);
                List<FunctionInt<RecordSet, Column>> columns = new ArrayList<>();
                GenColumn genColumn = new GenColumn(mgr);
                for (int i = 0; i < numColumns; i++)
                {
                    ExBiFunction<Integer, RecordSet, Column> col = genColumn.generate(r, generationStatus);
                    columns.add(rs -> col.apply(length, rs));
                }
                tables.add(new ImmediateDataSource(mgr, new EditableRecordSet(columns, () -> length)));
            }
            return new ImmediateData_Mgr(mgr, tables);
        }
        catch (InternalException | UserException e)
        {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public void configure(Precision p)
    {
        numTables = p.scale();
    }

/*
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
                columns.add(rs -> column._test_shrink(rs, shrunkLength));
            }
            //Could also remove arbitrary column(s)
            return Collections.singletonList(new ImmediateData_Mgr(mgr, new ImmediateDataSource(mgr, new KnownLengthRecordSet(larger.data.getData().getTitle(), columns, shrunkLength)), larger.dataB));
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return super.doShrink(random, larger);
        }
    }*/
}
