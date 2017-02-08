package test.gen;

import annotation.qual.Value;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import records.data.ColumnId;
import records.data.TableId;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.Concatenate;
import test.DummyManager;
import test.TestUtil;
import test.TestUtil.Transformation_Mgr;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assume.assumeNoException;

/**
 * Created by neil on 02/02/2017.
 */
public class GenNonsenseConcatenate extends Generator<Transformation_Mgr>
{
    public GenNonsenseConcatenate()
    {
        super(Transformation_Mgr.class);
    }

    @Override
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public Transformation_Mgr generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
    {
        TableId ourId = TestUtil.generateTableId(sourceOfRandomness);
        List<TableId> srcIds = TestUtil.makeList(sourceOfRandomness, 1, 5, () -> TestUtil.generateTableId(sourceOfRandomness));

        int numMissingCols = sourceOfRandomness.nextInt(0, 10);
        GenValueAnyType genValue = new GenValueAnyType();
        Map<ColumnId, Optional<@Value Object>> missing = new HashMap<>();
        for (int i = 0; i < numMissingCols; i++)
        {
            missing.put(TestUtil.generateColumnId(sourceOfRandomness), sourceOfRandomness.nextBoolean() ? Optional.empty() : Optional.of(genValue.generate(sourceOfRandomness, generationStatus)));
        }

        try
        {
            DummyManager mgr = new DummyManager();
            return new Transformation_Mgr(mgr, new Concatenate(mgr, ourId, srcIds, missing));
        }
        catch (InternalException | UserException e)
        {
            assumeNoException(e);
            throw new RuntimeException(e);
        }
    }
}
