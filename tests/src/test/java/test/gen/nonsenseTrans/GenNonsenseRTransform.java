package test.gen.nonsenseTrans;

import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import records.data.DataTestUtil;
import records.data.TableId;
import xyz.columnal.error.InternalException;
import records.transformations.RTransformation;
import test.DummyManager;
import test.TestUtil;
import test.TestUtil.Transformation_Mgr;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * Created by neil on 27/11/2016.
 */
public class GenNonsenseRTransform extends Generator<Transformation_Mgr>
{
    public GenNonsenseRTransform()
    {
        super(Transformation_Mgr.class);
    }

    @Override
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public Transformation_Mgr generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
    {
        try
        {
            DummyManager mgr = TestUtil.managerWithTestTypes().getFirst();

            ImmutableList<TableId> srcIds = DataTestUtil.makeList(sourceOfRandomness, 0, 5, () -> TestUtil.generateTableId(sourceOfRandomness));
            ImmutableList<String> pkgs = DataTestUtil.makeList(sourceOfRandomness, 0, 10, () -> DataTestUtil.generateIdent(sourceOfRandomness));
            String rExpression = TestUtil.makeNonEmptyString(sourceOfRandomness, generationStatus);
            
            return new Transformation_Mgr(mgr, new RTransformation(mgr, TestUtil.ILD, srcIds, pkgs, rExpression));
        }
        catch (InternalException e)
        {
            throw new RuntimeException(e);
        }
    }
}
