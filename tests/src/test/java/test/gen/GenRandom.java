package test.gen;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;

import java.util.Random;

/**
 * Created by neil on 29/11/2016.
 */
public class GenRandom extends Generator<Random>
{
    public GenRandom()
    {
        super(Random.class);
    }

    @Override
    public Random generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
    {
        return new Random(sourceOfRandomness.nextLong());
    }
}
