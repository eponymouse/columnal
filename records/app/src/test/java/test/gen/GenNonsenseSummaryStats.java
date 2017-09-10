package test.gen;

import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import records.data.ColumnId;
import records.data.TableId;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.SummaryStatistics;
import records.transformations.expression.CallExpression;
import records.transformations.expression.ColumnReference;
import records.transformations.expression.ColumnReference.ColumnReferenceType;
import records.transformations.expression.Expression;
import test.DummyManager;
import test.TestUtil;
import test.TestUtil.Transformation_Mgr;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;

import java.util.List;
import java.util.Map;

/**
 * Created by neil on 16/11/2016.
 */
public class GenNonsenseSummaryStats extends Generator<Transformation_Mgr>
{
    public GenNonsenseSummaryStats()
    {
        super(Transformation_Mgr.class);
    }

    @Override
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public Transformation_Mgr generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
    {
        Pair<TableId, TableId> ids = TestUtil.generateTableIdPair(sourceOfRandomness);
        List<ColumnId> splitBy = TestUtil.makeList(sourceOfRandomness, 0, 4, () -> TestUtil.generateColumnId(sourceOfRandomness));
        List<Pair<ColumnId, Expression>> summaries = TestUtil.makeList(sourceOfRandomness, 1, 5, () -> new Pair<>(TestUtil.generateColumnId(sourceOfRandomness),
            new CallExpression("count", new ColumnReference(TestUtil.generateColumnId(sourceOfRandomness), ColumnReferenceType.WHOLE_COLUMN))));
        try
        {
            DummyManager mgr = new DummyManager();
            return new Transformation_Mgr(mgr, new SummaryStatistics(mgr, ids.getFirst(), ids.getSecond(), ImmutableList.copyOf(summaries), ImmutableList.copyOf(splitBy)));
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }
}
