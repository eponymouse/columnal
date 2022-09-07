package test.gen;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import xyz.columnal.data.DataTestUtil;
import test.TestUtil;

import java.time.LocalDateTime;
import java.time.OffsetTime;

/**
 * Created by neil on 15/12/2016.
 */
public class GenOffsetTime extends Generator<OffsetTime>
{
    public GenOffsetTime()
    {
        super(OffsetTime.class);
    }

    @Override
    public OffsetTime generate(SourceOfRandomness r, GenerationStatus generationStatus)
    {
        return OffsetTime.of(r.nextInt(24), r.nextInt(60), r.nextInt(60), r.nextInt(1000000000), DataTestUtil.generateZoneOffset(r, generationStatus));
    }
}
