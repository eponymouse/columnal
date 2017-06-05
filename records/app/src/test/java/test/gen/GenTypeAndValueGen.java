package test.gen;

import annotation.qual.Value;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;
import test.gen.GenTypeAndValueGen.TypeAndValueGen;
import utility.Pair;
import utility.SimulationSupplier;

import java.util.function.Supplier;

/**
 * Created by neil on 05/06/2017.
 */
public class GenTypeAndValueGen extends GenValueBase<TypeAndValueGen>
{
    public class TypeAndValueGen
    {
        private final DataType type;

        public TypeAndValueGen(DataType type)
        {
            this.type = type;
        }

        public DataType getType()
        {
            return type;
        }

        public @Value Object makeValue() throws InternalException, UserException
        {
            return GenTypeAndValueGen.this.makeValue(type);
        }
    }

    public GenTypeAndValueGen()
    {
        super(TypeAndValueGen.class);
    }


    @Override
    public TypeAndValueGen generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
    {
        GenDataType genDataType = new GenDataType();
        DataType type = genDataType.generate(sourceOfRandomness, generationStatus);

        this.r = sourceOfRandomness;
        this.gs = generationStatus;

        return new TypeAndValueGen(type);
    }
}
