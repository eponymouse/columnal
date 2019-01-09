package test.gen;

import com.pholser.junit.quickcheck.generator.Generator;
import test.gen.GenDataType.DataTypeAndManager;
import test.gen.GenDataType.GenTaggedType;

/**
 * Created by neil on 05/06/2017.
 */
public class GenTaggedTypeAndValueGen extends GenTypeAndValueGen
{
    @Override
    public Generator<DataTypeAndManager> typeGenerator()
    {
        return new GenTaggedType();
    }
}
