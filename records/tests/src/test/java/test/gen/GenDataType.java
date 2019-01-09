package test.gen;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DataTypeVisitorEx;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.TagType;
import records.data.datatype.NumberInfo;
import records.data.datatype.TypeId;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import test.gen.GenDataType.DataTypeAndManager;
import test.gen.GenJellyType.JellyTypeAndManager;
import test.gen.GenJellyType.TypeKinds;
import utility.Either;

/**
 * Created by neil on 13/01/2017.
 */
public class GenDataType extends Generator<DataTypeAndManager>
{
    private final GenJellyType genJellyType;
    private final boolean mustHaveValues;

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
        this(false);
    }

    public GenDataType(boolean mustHaveValues)
    {
        // All kinds:
        this(ImmutableSet.copyOf(TypeKinds.values()), mustHaveValues);
    }

    public GenDataType(ImmutableSet<TypeKinds> typeKinds, boolean mustHaveValues)
    {
        super(DataTypeAndManager.class);
        genJellyType = new GenJellyType(typeKinds, ImmutableSet.of(), mustHaveValues);
        this.mustHaveValues = mustHaveValues;
    }
    
    @Override
    public DataTypeAndManager generate(SourceOfRandomness r, GenerationStatus generationStatus)
    {
        JellyTypeAndManager jellyTypeAndManager = genJellyType.generate(r, generationStatus);
        try
        {
            DataType dataType;
            do
            {
                dataType = jellyTypeAndManager.jellyType.makeDataType(ImmutableMap.of(), jellyTypeAndManager.typeManager);
            }
            while (mustHaveValues && !hasValues(dataType));
            
            
            return new DataTypeAndManager(jellyTypeAndManager.typeManager, dataType);
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }

    private static boolean hasValues(DataType dataType) throws InternalException
    {
        return dataType.apply(new DataTypeVisitorEx<Boolean, InternalException>()
        {
            @Override
            public Boolean number(NumberInfo numberInfo) throws InternalException
            {
                return true;
            }

            @Override
            public Boolean text() throws InternalException
            {
                return true;
            }

            @Override
            public Boolean date(DateTimeInfo dateTimeInfo) throws InternalException
            {
                return true;
            }

            @Override
            public Boolean bool() throws InternalException
            {
                return true;
            }

            @Override
            public Boolean tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException
            {
                return !tags.isEmpty();
            }

            @Override
            public Boolean tuple(ImmutableList<DataType> inner) throws InternalException
            {
                for (DataType type : inner)
                {
                    if (!hasValues(type))
                    {
                        return false;
                    }
                }
                return true;
            }

            @Override
            public Boolean array(DataType inner) throws InternalException
            {
                return hasValues(inner);
            }
        });
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
