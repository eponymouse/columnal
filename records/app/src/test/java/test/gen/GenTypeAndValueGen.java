package test.gen;

import annotation.qual.Value;
import com.google.common.collect.ImmutableSet;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.error.InternalException;
import records.error.UserException;
import test.gen.GenDataType.DataTypeAndManager;
import test.gen.GenDataType.TypeKinds;
import test.gen.GenTypeAndValueGen.TypeAndValueGen;

/**
 * Created by neil on 05/06/2017.
 */
public class GenTypeAndValueGen extends GenValueBase<TypeAndValueGen>
{
    private final boolean onlyNumTextTemporal;

    public class TypeAndValueGen
    {
        private final DataType type;
        private final TypeManager typeManager;

        private TypeAndValueGen(DataType type, TypeManager typeManager)
        {
            this.type = type;
            this.typeManager = typeManager;
        }

        public DataType getType()
        {
            return type;
        }

        public TypeManager getTypeManager()
        {
            return typeManager;
        }

        public @Value Object makeValue() throws InternalException, UserException
        {
            return GenTypeAndValueGen.this.makeValue(type);
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
        this.onlyNumTextTemporal = onlyNumTextTemporal;
    }


    @Override
    public TypeAndValueGen generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
    {
        GenDataType genDataType = onlyNumTextTemporal ? new GenDataType(ImmutableSet.of(TypeKinds.NUM_TEXT_TEMPORAL)) : new GenDataType();
        DataTypeAndManager generated = genDataType.generate(sourceOfRandomness, generationStatus);
        this.r = sourceOfRandomness;
        this.gs = generationStatus;

        return new TypeAndValueGen(generated.dataType, generated.typeManager);
    }

}
