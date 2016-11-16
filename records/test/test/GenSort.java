package test;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import records.data.ColumnId;
import records.data.TableId;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.Sort;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.List;

import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assume.assumeNoException;
import static org.junit.Assume.assumeThat;

/**
 * Created by neil on 16/11/2016.
 */
public class GenSort extends Generator<Sort>
{
    public GenSort()
    {
        super(Sort.class);
    }

    @Override
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public Sort generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
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
        try
        {
            return new Sort(new DummyManager(), us, srcFinal, cols);
        }
        catch (UserException | InternalException e)
        {
            assumeNoException(e);
            throw new RuntimeException(e);
        }
    }
}
