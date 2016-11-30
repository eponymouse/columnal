package test.gen;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import records.data.ColumnId;
import records.data.TableId;
import records.error.InternalException;
import records.transformations.SummaryStatistics;
import records.transformations.SummaryStatistics.SummaryType;
import test.DummyManager;
import test.TestUtil;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;

import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Created by neil on 16/11/2016.
 */
public class GenSummaryStats extends Generator<SummaryStatistics>
{
    public GenSummaryStats()
    {
        super(SummaryStatistics.class);
    }

    @Override
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public SummaryStatistics generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
    {
        Pair<TableId, TableId> ids = TestUtil.generateTableIdPair(sourceOfRandomness);
        List<ColumnId> splitBy = TestUtil.makeList(sourceOfRandomness, 0, 4, () -> TestUtil.generateColumnId(sourceOfRandomness));
        Map<ColumnId, TreeSet<SummaryType>> summaries = TestUtil.makeMap(sourceOfRandomness, 1, 5, () -> TestUtil.generateColumnId(sourceOfRandomness),
            () -> new TreeSet<>(TestUtil.makeList(sourceOfRandomness, 1, 5, () -> sourceOfRandomness.choose(SummaryType.values()))));
        try
        {
            return new SummaryStatistics(new DummyManager(), ids.getFirst(), ids.getSecond(), summaries, splitBy);
        }
        catch (InternalException e)
        {
            throw new RuntimeException(e);
        }
    }
}
