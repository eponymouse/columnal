package test.gen;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import records.data.ColumnId;
import records.data.TableId;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.Sort;
import test.DummyManager;
import test.TestUtil;
import test.TestUtil.Transformation_Mgr;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;

import java.util.List;

import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assume.assumeNoException;
import static org.junit.Assume.assumeThat;

/**
 * Created by neil on 16/11/2016.
 */
public class GenNonsenseSort extends Generator<Transformation_Mgr>
{
    public GenNonsenseSort()
    {
        super(Transformation_Mgr.class);
    }

    @Override
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public Transformation_Mgr generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
    {
        Pair<TableId, TableId> ids = TestUtil.generateTableIdPair(sourceOfRandomness);
        List<ColumnId> cols = TestUtil.makeList(sourceOfRandomness, 1, 10, () -> TestUtil.generateColumnId(sourceOfRandomness));

        try
        {
            DummyManager mgr = new DummyManager();
            return new Transformation_Mgr(mgr, new Sort(mgr, ids.getFirst(), ids.getSecond(), cols));
        }
        catch (InternalException | UserException e)
        {
            assumeNoException(e);
            throw new RuntimeException(e);
        }
    }
}
