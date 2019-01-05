package test.gen;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import records.data.Table.InitialLoadDetails;
import records.data.TableId;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.Filter;
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
public class GenNonsenseFilter extends Generator<Transformation_Mgr>
{
    public GenNonsenseFilter()
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
            return new Transformation_Mgr(mgr, new Filter(mgr, new InitialLoadDetails(ids.getFirst(), null, null), ids.getSecond(), nonsenseExpression));
        }
        catch (InternalException e)
        {
            throw new RuntimeException(e);
        }
    }
}
