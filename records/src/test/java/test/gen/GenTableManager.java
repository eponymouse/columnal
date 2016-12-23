package test.gen;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import records.data.TableManager;
import records.error.InternalException;
import records.error.UserException;
import test.DummyManager;

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
        try
        {
            return new DummyManager();
        }
        catch (UserException | InternalException e)
        {
            throw new RuntimeException(e);
        }
    }
}
