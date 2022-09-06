package test.gen.type;

import com.google.common.collect.ImmutableSet;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;
import test.gen.type.GenJellyTypeMaker.TypeKinds;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * A simplifying wrapper for GenDataTypeMaker that just gives the data type
 */
public class GenDataType extends Generator<DataType>
{
    private final GenDataTypeMaker genDataTypeMaker;
    
    public GenDataType()
    {
        // All kinds:
        this(false);
    }

    public GenDataType(boolean mustHaveValues)
    {
        // All kinds:
        this(ImmutableSet.copyOf(TypeKinds.values()), mustHaveValues);
    }

    public GenDataType(ImmutableSet<TypeKinds> typeKinds, boolean mustHaveValues)
    {
        super(DataType.class);
        genDataTypeMaker = new GenDataTypeMaker(typeKinds, mustHaveValues);
    }
    
    @Override
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public DataType generate(SourceOfRandomness r, GenerationStatus generationStatus)
    {
        try
        {
            return genDataTypeMaker.generate(r, generationStatus).makeType().getDataType();
        }
        catch (UserException | InternalException e)
        {
            throw new RuntimeException(e);
        }
    }

    /*
    public static class GenTaggedType extends Generator<DataTypeAndManager>
    {
        public GenTaggedType()
        {
            super(DataTypeAndManager.class);
        }

        @Override
        public DataTypeAndManager generate(SourceOfRandomness random, GenerationStatus status)
        {
            GenJellyType.GenTaggedType genJellyTagged = new GenJellyType.GenTaggedType();
            
            JellyTypeAndManager jellyTypeAndManager = genJellyTagged.generate(random, status);
            
            try
            {
                return new DataTypeAndManager(jellyTypeAndManager.typeManager, jellyTypeAndManager.jellyType.makeDataType(ImmutableMap.of(), jellyTypeAndManager.typeManager));
            }
            catch (InternalException | UserException e)
            {
                throw new RuntimeException(e);
            }
        }
    }
    */
}
