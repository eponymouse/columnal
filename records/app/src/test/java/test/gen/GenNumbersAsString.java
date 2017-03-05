package test.gen;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import test.TestUtil;

import java.util.List;

/**
 * Created by neil on 09/12/2016.
 */
public class GenNumbersAsString extends Generator<List<String>>
{
    @SuppressWarnings("unchecked")
    public GenNumbersAsString()
    {
        super((Class<List<String>>) (Class<?>) List.class);
    }

    @Override
    public List<String> generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
    {
        int length = sourceOfRandomness.nextInt(0, 100);
        return TestUtil.makeList(length, new GenNumberAsString(), sourceOfRandomness, generationStatus);
    }
}
