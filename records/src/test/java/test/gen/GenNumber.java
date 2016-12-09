package test.gen;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;

/**
 * Created by neil on 09/12/2016.
 */
public class GenNumber extends Generator<String>
{
    public GenNumber()
    {
        super(String.class);
    }

    @Override
    public String generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
    {
        boolean includeFractional = sourceOfRandomness.nextBoolean();
        int maxBits = sourceOfRandomness.nextInt(6, 80);
        if (includeFractional && sourceOfRandomness.nextBoolean())
        {
            // I don't think it matters here whether we come up with
            // double or big decimal; will be stored in big decimal either way.
            return String.format("%f", sourceOfRandomness.nextDouble());
        }
        else
        {
            // We geometrically distribute by uniformly distributing number of bits:
            return sourceOfRandomness.nextBigInteger(sourceOfRandomness.nextInt(1, maxBits)).toString();
        }
    }
}
