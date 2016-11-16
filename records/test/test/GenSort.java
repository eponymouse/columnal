package test;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import records.data.ColumnId;
import records.data.TableId;
import records.data.TableManager;
import records.transformations.Sort;
import test.GenSort.MakeSort;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExFunction;

import java.util.List;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assume.assumeThat;

/**
 * Created by neil on 16/11/2016.
 */
public class GenSort extends Generator<MakeSort>
{
    public GenSort()
    {
        super(MakeSort.class);
    }

    public static interface MakeSort extends ExFunction<TableManager, Sort> { }

    @Override
    @OnThread(value = Tag.Simulation,ignoreParent = true)
    public MakeSort generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
    {
        TableId us = TestUtil.generateTableId(sourceOfRandomness);
        TableId src;
        do
        {
            src = TestUtil.generateTableId(sourceOfRandomness);
        }
        while (src.equals(us));
        List<ColumnId> cols = TestUtil.makeList(sourceOfRandomness, 1, 10, () -> TestUtil.generateColumnId(sourceOfRandomness));

        TableId srcFinal = src;
        return m -> new Sort(m, us, srcFinal, cols);
    }
}
