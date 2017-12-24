package test.gen;

import annotation.qual.Value;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;
import test.gen.GenDataType.GenTaggedType;
import test.gen.GenTypeAndValueGen.TypeAndValueGen;

/**
 * Created by neil on 05/06/2017.
 */
public class GenTaggedTypeAndValueGen extends GenTypeAndValueGen
{
    @Override
    public GenDataType typeGenerator()
    {
        return new GenTaggedType();
    }
}
