package test.gen;

import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import records.data.ColumnId;
import records.data.Table.InitialLoadDetails;
import records.data.TableId;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.Sort;
import records.transformations.Sort.Direction;
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
        ImmutableList<Pair<ColumnId, Direction>> cols = TestUtil.makeList(sourceOfRandomness, 1, 10, () -> new Pair<>(TestUtil.generateColumnId(sourceOfRandomness), sourceOfRandomness.nextBoolean() ? Direction.ASCENDING : Direction.DESCENDING));

        try
        {
            DummyManager mgr = new DummyManager();
            return new Transformation_Mgr(mgr, new Sort(mgr, new InitialLoadDetails(ids.getFirst(), null, null), ids.getSecond(), cols));
        }
        catch (InternalException | UserException e)
        {
            assumeNoException(e);
            throw new RuntimeException(e);
        }
    }
}
