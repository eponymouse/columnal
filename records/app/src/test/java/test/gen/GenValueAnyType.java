package test.gen;

import annotation.qual.Value;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import records.error.InternalException;
import records.error.UserException;
import test.TestUtil;

/**
 * Created by neil on 07/02/2017.
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
