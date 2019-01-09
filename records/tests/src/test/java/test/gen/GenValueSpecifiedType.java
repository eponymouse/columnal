package test.gen;

import annotation.qual.Value;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;
import test.TestUtil;
import test.gen.GenValueSpecifiedType.ValueGenerator;

import java.util.function.Function;

/**
 * Generates a value of a type specified by the caller.
 */
public class GenValueSpecifiedType extends GenValueBase<ValueGenerator>
{
    @SuppressWarnings("value")
    public GenValueSpecifiedType()
    {
        super(ValueGenerator.class);
    }

    @Override
    public ValueGenerator generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
    {
        // Not sure if this will mess with randomness by storing the items for later use:
        this.r = sourceOfRandomness;
        this.gs = generationStatus;
        return this::makeValue;
    }
    
    public interface ValueGenerator
    {
        public @Value Object makeValue(DataType t) throws InternalException, UserException;
    }
}
