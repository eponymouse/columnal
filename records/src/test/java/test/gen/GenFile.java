package test.gen;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import org.checkerframework.checker.nullness.qual.Nullable;
import test.TestTextFile;

import java.io.IOException;

/**
 * Created by neil on 26/10/2016.
 */
public class GenFile extends Generator<TestTextFile>
{
    public GenFile()
    {
        super(TestTextFile.class);
    }

    @Override
    @SuppressWarnings("nullness")
    public @Nullable TestTextFile generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
    {
        try
        {
            return new TestTextFile(sourceOfRandomness);
        }
        catch (IOException e)
        {
            return null;
        }
    }
}
