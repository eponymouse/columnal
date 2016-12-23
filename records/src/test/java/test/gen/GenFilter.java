package test.gen;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import records.data.TableId;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.Filter;
import test.DummyManager;
import test.TestUtil;
import test.TestUtil.Transformation_Mgr;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;

/**
 * Created by neil on 27/11/2016.
 */
public class GenFilter extends Generator<Transformation_Mgr>
{
    public GenFilter()
    {
        super(Transformation_Mgr.class);
    }

    @Override
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public Transformation_Mgr generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
    {
        Pair<TableId, TableId> ids = TestUtil.generateTableIdPair(sourceOfRandomness);
        try
        {
            DummyManager mgr = new DummyManager();
            return new Transformation_Mgr(mgr, new Filter(mgr, ids.getFirst(), ids.getSecond(), gen().make(GenNonsenseExpression.class).generate(sourceOfRandomness, generationStatus)));
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }
}
