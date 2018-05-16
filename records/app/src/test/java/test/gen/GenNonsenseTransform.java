package test.gen;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import records.data.ColumnId;
import records.data.Table.InitialLoadDetails;
import records.data.TableId;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.Calculate;
import records.transformations.expression.Expression;
import test.DummyManager;
import test.TestUtil;
import test.TestUtil.Transformation_Mgr;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by neil on 27/11/2016.
 */
public class GenNonsenseTransform extends Generator<Transformation_Mgr>
{
    public GenNonsenseTransform()
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
            mgr.getTypeManager()._test_copyTaggedTypesFrom(DummyManager.INSTANCE.getTypeManager());
            GenNonsenseExpression genNonsenseExpression = new GenNonsenseExpression();
            genNonsenseExpression.setTableManager(mgr);

            ImmutableMap.Builder<ColumnId, Expression> columns = ImmutableMap.builder();
            int numColumns = sourceOfRandomness.nextInt(0, 5);
            for (int i = 0; i < numColumns; i++)
            {
                Expression nonsenseExpression = genNonsenseExpression.generate(sourceOfRandomness, generationStatus);
                columns.put(TestUtil.generateColumnId(sourceOfRandomness), nonsenseExpression);
            }
            return new Transformation_Mgr(mgr, new Calculate(mgr, new InitialLoadDetails(ids.getFirst(), null, null), ids.getSecond(), columns.build()));
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }
}
