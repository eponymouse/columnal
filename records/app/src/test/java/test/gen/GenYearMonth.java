package test.gen;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;

import java.time.LocalTime;
import java.time.YearMonth;

/**
 * Created by neil on 15/12/2016.
 */
public class GenYearMonth extends Generator<YearMonth>
{
    public GenYearMonth()
    {
        super(YearMonth.class);
    }

    @Override
    public YearMonth generate(SourceOfRandomness r, GenerationStatus generationStatus)
    {
        return YearMonth.of(r.nextInt(1900, 2200), r.nextInt(12) + 1);
    }
}
