package test.gen;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import records.data.TableId;
import test.TestUtil;

public class GenTableId extends Generator<TableId>
{
    public GenTableId()
    {
        super(TableId.class);
    }

    @Override
    public TableId generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
    {
        return TestUtil.generateTableId(sourceOfRandomness);
    }
}
