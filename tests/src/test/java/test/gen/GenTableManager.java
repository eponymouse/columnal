package test.gen;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import records.data.TableManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import test.DummyManager;
import test.TestUtil;

/**
 * No randomness involved; this is just an easy way to pass a fresh table manager to
 * a property test.
 */
public class GenTableManager extends Generator<TableManager>
{
    public GenTableManager()
    {
        super (TableManager.class);
    }

    @Override
    public TableManager generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
    {
        return TestUtil.managerWithTestTypes().getFirst();
    }
}
