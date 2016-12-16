package test.gen;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.generator.java.time.ZoneIdGenerator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;

import java.time.ZoneId;

/**
 * Created by neil on 16/12/2016.
 */
public class GenZoneId extends Generator<ZoneId>
{
    public GenZoneId()
    {
        super(ZoneId.class);
    }

    @Override
    public ZoneId generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
    {
        ZoneIdGenerator genZoneId = new ZoneIdGenerator();
        // Don't generate unrecognisable SystemV timezones:
        ZoneId zone;
        do
        {
            zone = genZoneId.generate(sourceOfRandomness, generationStatus);
        }
        while (zone.toString().contains("SystemV") || zone.toString().contains("GMT0"));
        return zone;
    }
}
