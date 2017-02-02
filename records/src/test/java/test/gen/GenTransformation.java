package test.gen;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import test.TestUtil.Transformation_Mgr;

import java.util.Arrays;
import java.util.List;

/**
 * Created by neil on 02/02/2017.
 */
public class GenTransformation extends Generator<Transformation_Mgr>
{
    List<Generator<Transformation_Mgr>> generators = Arrays.asList(
        new GenFilter(),
        new GenHideColumns(),
        new GenSort(),
        new GenSummaryStats()
    );

    public GenTransformation()
    {
        super(Transformation_Mgr.class);
    }

    @Override
    public Transformation_Mgr generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
    {
        return sourceOfRandomness.choose(generators).generate(sourceOfRandomness, generationStatus);
    }
}
