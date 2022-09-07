package test.gen.type;

import annotation.qual.Value;
import com.google.common.collect.ImmutableSet;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.internal.GeometricDistribution;
import com.pholser.junit.quickcheck.internal.generator.SimpleGenerationStatus;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import test.gen.type.GenDataTypeMaker.DataTypeAndValueMaker;
import test.gen.type.GenDataTypeMaker.DataTypeMaker;
import test.gen.type.GenJellyTypeMaker.TypeKinds;
import test.gen.type.GenTypeAndValueGen.TypeAndValueGen;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.Random;

/**
 * Generates a class which wraps a type and allows for generation of
 * as many values of that type as you want.
 * 
 * A thin simplifying wrapper over GenDataTypeMaker that makes one type
 * and sticks with it.
 */
public class GenTypeAndValueGen extends Generator<TypeAndValueGen>
{
    private final GenDataTypeMaker genDataTypeMaker;

    public class TypeAndValueGen
    {
        private final DataTypeAndValueMaker dataTypeAndValueMaker;

        public TypeAndValueGen(DataTypeAndValueMaker dataTypeAndValueMaker)
        {
            this.dataTypeAndValueMaker = dataTypeAndValueMaker;
        }

        public DataType getType()
        {
            return dataTypeAndValueMaker.getDataType();
        }

        public TypeManager getTypeManager()
        {
            return dataTypeAndValueMaker.getTypeManager();
        }

        public @Value Object makeValue() throws InternalException, UserException
        {
            return dataTypeAndValueMaker.makeValue();
        }
    }

    public GenTypeAndValueGen()
    {
        this(false);
    }

    // If false, can be any type
    public GenTypeAndValueGen(boolean onlyNumTextTemporal)
    {
        super(TypeAndValueGen.class);
        this.genDataTypeMaker = onlyNumTextTemporal ?
            new GenDataTypeMaker(ImmutableSet.of(TypeKinds.NUM_TEXT_TEMPORAL), true) :        
            new GenDataTypeMaker(true);
    }


    @Override
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public TypeAndValueGen generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
    {
        try
        {
            DataTypeMaker generated = genDataTypeMaker.generate(sourceOfRandomness, generationStatus);
            DataTypeAndValueMaker result = generated.makeType();
            return new TypeAndValueGen(result);
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }
    
    public TypeAndValueGen generate(Random r)
    {
        SourceOfRandomness sourceOfRandomness = new SourceOfRandomness(r);
        return generate(sourceOfRandomness, new SimpleGenerationStatus(new GeometricDistribution(), sourceOfRandomness, 1));
    }

}
