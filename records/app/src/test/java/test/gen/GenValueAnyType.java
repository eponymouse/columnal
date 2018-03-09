package test.gen;

import annotation.qual.Value;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import records.error.InternalException;
import records.error.UserException;
import test.TestUtil;

/**
 * Generates a value of any type (decided by randomness,
 * not by caller)
 */
public class GenValueAnyType extends GenValueBase<@Value Object>
{
    @SuppressWarnings("value")
    public GenValueAnyType()
    {
        super((Class<@Value Object>)Object.class);
    }

    @Override
    public @Value Object generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
    {
        this.r = sourceOfRandomness;
        this.gs = generationStatus;
        try
        {
            return makeValue(sourceOfRandomness.choose(TestUtil.distinctTypes));
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }
}
