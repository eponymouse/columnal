package test.gen;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;

import java.math.BigInteger;
import java.util.Arrays;

/**
 * Created by neil on 09/12/2016.
 */
public class GenNumberAsString extends Generator<String>
{
    private final boolean fixBits;
    private int maxBits = -1;

    /**
     *
     * @param fixBits If true then
     */
    public GenNumberAsString(boolean fixBits)
    {
        super(String.class);
        this.fixBits = fixBits;
    }

    public GenNumberAsString()
    {
        this(false);
    }

    @Override
    public String generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
    {
        if (sourceOfRandomness.nextBoolean())
        {
            // Use awkward numbers:
            return sourceOfRandomness.choose(Arrays.<Number>asList(
                0, 1, -1,
                Byte.MIN_VALUE, Byte.MIN_VALUE + 1, Byte.MAX_VALUE,
                Short.MIN_VALUE, Short.MIN_VALUE + 1, Short.MAX_VALUE,
                Integer.MIN_VALUE, Integer.MIN_VALUE + 1, Integer.MAX_VALUE,
                (long)Integer.MIN_VALUE - 1L, (long)Integer.MAX_VALUE + 1L,
                Long.MIN_VALUE, Long.MIN_VALUE + 1, Long.MAX_VALUE,
                BigInteger.valueOf(Long.MIN_VALUE).subtract(BigInteger.ONE),
                BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE)
            )).toString();
        }
        if (!fixBits || maxBits == -1)
        {
            maxBits = sourceOfRandomness.nextInt(3, 80);
        }
        boolean includeFractional = maxBits >= 48;
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
