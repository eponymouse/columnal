package test.gen.nonsenseTrans;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import records.data.Table.InitialLoadDetails;
import records.data.TableId;
import records.error.InternalException;
import records.transformations.Check;
import records.transformations.Check.CheckType;
import records.transformations.expression.Expression;
import test.DummyManager;
import test.TestUtil;
import test.TestUtil.Transformation_Mgr;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;

/**
 * Created by neil on 27/11/2016.
 */
public class GenNonsenseCheck extends Generator<Transformation_Mgr>
{
    public GenNonsenseCheck()
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
            DummyManager mgr = TestUtil.managerWithTestTypes().getFirst();
            GenNonsenseExpression genNonsenseExpression = new GenNonsenseExpression();
            genNonsenseExpression.setTableManager(mgr);
            Expression nonsenseExpression = genNonsenseExpression.generate(sourceOfRandomness, generationStatus);
            return new Transformation_Mgr(mgr, new Check(mgr, new InitialLoadDetails(ids.getFirst(), null, null, null), ids.getSecond(), CheckType.values()[sourceOfRandomness.nextInt(CheckType.values().length)], nonsenseExpression));
        }
        catch (InternalException e)
        {
            throw new RuntimeException(e);
        }
    }
}
