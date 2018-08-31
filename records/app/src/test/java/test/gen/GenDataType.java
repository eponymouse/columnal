package test.gen;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.error.InternalException;
import records.error.UserException;
import test.gen.GenDataType.DataTypeAndManager;
import test.gen.GenJellyType.JellyTypeAndManager;
import test.gen.GenJellyType.TypeKinds;

/**
 * Created by neil on 13/01/2017.
 */
public class GenDataType extends Generator<DataTypeAndManager>
{
    private final GenJellyType genJellyType;

    public static class DataTypeAndManager
    {
        public final TypeManager typeManager;
        public final DataType dataType;

        public DataTypeAndManager(TypeManager typeManager, DataType dataType) throws InternalException
        {
            this.typeManager = typeManager;
            this.dataType = dataType;
        }
    }
    
    public GenDataType()
    {
        // All kinds:
        this(ImmutableSet.copyOf(TypeKinds.values()));
    }

    public GenDataType(ImmutableSet<TypeKinds> typeKinds)
    {
        super(DataTypeAndManager.class);
        genJellyType = new GenJellyType(typeKinds, ImmutableSet.of());
    }
    
    @Override
    public DataTypeAndManager generate(SourceOfRandomness r, GenerationStatus generationStatus)
    {
        JellyTypeAndManager jellyTypeAndManager = genJellyType.generate(r, generationStatus);
        try
        {
            return new DataTypeAndManager(jellyTypeAndManager.typeManager, jellyTypeAndManager.jellyType.makeDataType(ImmutableMap.of(), jellyTypeAndManager.typeManager));
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }
    
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
}
