package test.gen.nonsenseTrans;

import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import records.data.DataTestUtil;
import records.data.Table.InitialLoadDetails;
import records.data.TableId;
import records.error.InternalException;
import records.transformations.Concatenate;
import records.transformations.Concatenate.IncompleteColumnHandling;
import test.DummyManager;
import test.TestUtil;
import test.TestUtil.Transformation_Mgr;
import test.gen.GenValueBase;
import threadchecker.OnThread;
import threadchecker.Tag;

import static org.junit.Assume.assumeNoException;

/**
 * Created by neil on 02/02/2017.
 */
public class GenNonsenseConcatenate extends GenValueBase<Transformation_Mgr>
{
    public GenNonsenseConcatenate()
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
        ImmutableList<TableId> srcIds = DataTestUtil.makeList(sourceOfRandomness, 1, 5, () -> TestUtil.generateTableId(sourceOfRandomness));

        IncompleteColumnHandling incompleteColumnHandling = IncompleteColumnHandling.values()[sourceOfRandomness.nextInt(IncompleteColumnHandling.values().length)];

        try
        {
            return new Transformation_Mgr(mgr, new Concatenate(mgr, new InitialLoadDetails(ourId, null, null, null), srcIds, incompleteColumnHandling, r.nextBoolean()));
        }
        catch (InternalException e)
        {
            assumeNoException(e);
            throw new RuntimeException(e);
        }
    }

}
