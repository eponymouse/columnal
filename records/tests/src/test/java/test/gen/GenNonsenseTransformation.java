package test.gen;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import test.TestUtil.Transformation_Mgr;

import java.util.Arrays;
import java.util.List;

/**
 * Generates a transformation which can be successfully loaded and saved,
 * but not necessarily executed successfully.
 */
public class GenNonsenseTransformation extends Generator<Transformation_Mgr>
{
    List<Generator<Transformation_Mgr>> generators = Arrays.asList(
        new GenNonsenseCheck(),
        new GenNonsenseConcatenate(),
        new GenNonsenseFilter(),
        new GenNonsenseJoin(),
        new GenNonsenseHideColumns(),
        new GenNonsenseManualEdit(),
        new GenNonsenseSort(),
        new GenNonsenseSummaryStats(),
        new GenNonsenseTransform()
    );

    public GenNonsenseTransformation()
    {
        super(Transformation_Mgr.class);
    }

    @Override
    public Transformation_Mgr generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
    {
        return sourceOfRandomness.choose(generators).generate(sourceOfRandomness, generationStatus);
    }
}
