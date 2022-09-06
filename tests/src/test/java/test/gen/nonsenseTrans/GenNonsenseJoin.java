package test.gen.nonsenseTrans;

import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import records.data.ColumnId;
import records.data.DataTestUtil;
import records.data.Table.InitialLoadDetails;
import records.data.TableId;
import records.error.InternalException;
import records.transformations.Join;
import test.DummyManager;
import test.TestUtil;
import test.TestUtil.Transformation_Mgr;
import test.gen.GenValueBase;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;

import static org.junit.Assume.assumeNoException;

/**
 * Created by neil on 02/02/2017.
 */
public class GenNonsenseJoin extends GenValueBase<Transformation_Mgr>
{
    public GenNonsenseJoin()
    {
        super(Transformation_Mgr.class);
    }

    @Override
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public Transformation_Mgr generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
    {
        this.r = sourceOfRandomness;
        this.gs = generationStatus;

        DummyManager mgr = TestUtil.managerWithTestTypes().getFirst();

        TableId ourId = TestUtil.generateTableId(sourceOfRandomness);
        TableId srcIdA = TestUtil.generateTableId(sourceOfRandomness);
        TableId srcIdB = TestUtil.generateTableId(sourceOfRandomness);
        ImmutableList<Pair<ColumnId, ColumnId>> columns = DataTestUtil.makeList(r, 0, 5, () -> new Pair<>(TestUtil.generateColumnId(r), TestUtil.generateColumnId(r)));

        try
        {
            return new Transformation_Mgr(mgr, new Join(mgr, new InitialLoadDetails(ourId, null, null, null), srcIdA, srcIdB, r.nextBoolean(), columns));
        }
        catch (InternalException e)
        {
            assumeNoException(e);
            throw new RuntimeException(e);
        }
    }

}
