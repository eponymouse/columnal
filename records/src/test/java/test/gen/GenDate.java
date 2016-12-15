package test.gen;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import test.TestUtil;

import java.time.LocalDate;

/**
 * Created by neil on 15/12/2016.
 */
public class GenDate extends Generator<LocalDate>
{
    public GenDate()
    {
        super(LocalDate.class);
    }

    @Override
    public LocalDate generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
    {
        return TestUtil.generateDate(sourceOfRandomness, generationStatus);
    }
}
