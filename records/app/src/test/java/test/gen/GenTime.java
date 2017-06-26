package test.gen;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import test.TestUtil;

import java.time.LocalTime;
import java.time.OffsetTime;

/**
 * Created by neil on 15/12/2016.
 */
public class GenTime extends Generator<LocalTime>
{
    public GenTime()
    {
        super(LocalTime.class);
    }

    @Override
    public LocalTime generate(SourceOfRandomness r, GenerationStatus generationStatus)
    {
        return LocalTime.of(r.nextInt(24), r.nextInt(60), r.nextInt(60), r.nextInt(1000000000));
    }
}
