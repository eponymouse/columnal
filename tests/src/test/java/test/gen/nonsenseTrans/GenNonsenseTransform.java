package test.gen.nonsenseTrans;

import com.google.common.collect.ImmutableMap;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import records.data.ColumnId;
import records.data.Table.InitialLoadDetails;
import records.data.TableId;
import xyz.columnal.error.InternalException;
import records.transformations.Calculate;
import records.transformations.expression.Expression;
import test.DummyManager;
import test.TestUtil;
import test.TestUtil.Transformation_Mgr;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Pair;

import java.util.HashMap;

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
            DummyManager mgr = TestUtil.managerWithTestTypes().getFirst();
            GenNonsenseExpression genNonsenseExpression = new GenNonsenseExpression();
            genNonsenseExpression.setTableManager(mgr);

            HashMap<ColumnId, Expression> columns = new HashMap<>();
            int numColumns = sourceOfRandomness.nextInt(0, 5);
            for (int i = 0; i < numColumns; i++)
            {
                Expression nonsenseExpression = genNonsenseExpression.generate(sourceOfRandomness, generationStatus);
                ColumnId columnId = TestUtil.generateColumnId(sourceOfRandomness);
                while (columns.containsKey(columnId))
                    columnId = TestUtil.generateColumnId(sourceOfRandomness);
                columns.put(columnId, nonsenseExpression);
            }
            return new Transformation_Mgr(mgr, new Calculate(mgr, new InitialLoadDetails(ids.getFirst(), null, null, null), ids.getSecond(), ImmutableMap.copyOf(columns)));
        }
        catch (InternalException e)
        {
            throw new RuntimeException(e);
        }
    }
}
