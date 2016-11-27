package test;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import records.data.TableId;
import records.error.InternalException;
import records.transformations.Filter;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;

/**
 * Created by neil on 27/11/2016.
 */
public class GenFilter extends Generator<Filter>
{
    public GenFilter()
    {
        super(Filter.class);
    }

    @Override
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public Filter generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
    {
        Pair<TableId, TableId> ids = TestUtil.generateTableIdPair(sourceOfRandomness);
        try
        {
            return new Filter(new DummyManager(), ids.getFirst(), ids.getSecond(), gen().make(GenExpression.class).generate(sourceOfRandomness, generationStatus));
        }
        catch (InternalException e)
        {
            throw new RuntimeException(e);
        }
    }
}
