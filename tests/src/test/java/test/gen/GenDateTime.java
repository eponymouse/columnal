package test.gen;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import xyz.columnal.data.DataTestUtil;
import test.TestUtil;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Created by neil on 15/12/2016.
 */
public class GenDateTime extends Generator<LocalDateTime>
{
    public GenDateTime()
    {
        super(LocalDateTime.class);
    }

    @Override
    public LocalDateTime generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
    {
        return DataTestUtil.generateDateTime(sourceOfRandomness, generationStatus);
    }
}
