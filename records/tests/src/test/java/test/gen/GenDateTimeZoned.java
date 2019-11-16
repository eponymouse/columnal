package test.gen;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import records.data.DataTestUtil;
import test.TestUtil;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;

/**
 * Created by neil on 15/12/2016.
 */
public class GenDateTimeZoned extends Generator<ZonedDateTime>
{
    public GenDateTimeZoned()
    {
        super(ZonedDateTime.class);
    }

    @Override
    public ZonedDateTime generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
    {
        return DataTestUtil.generateDateTimeZoned(sourceOfRandomness, generationStatus);
    }
}
