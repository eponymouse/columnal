package test.gen;

import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.TaggedTypeDefinition;
import records.data.datatype.TaggedTypeDefinition.TypeVariableKind;
import records.data.datatype.TypeId;
import records.error.InternalException;
import records.error.UserException;
import records.jellytype.JellyType;
import test.TestUtil;
import utility.Either;
import utility.Pair;
import utility.Utility;

import java.util.ArrayList;

public class GenTaggedTypeDefinition extends Generator<TaggedTypeDefinition>
{
    public GenTaggedTypeDefinition()
    {
        super(TaggedTypeDefinition.class);
    }
    
    @Override
    public TaggedTypeDefinition generate(SourceOfRandomness r, GenerationStatus status)
    {
        try
        {
            GenDataType genDataType = new GenDataType();
            final ImmutableList<Pair<TypeVariableKind, String>> typeVars;
            if (r.nextBoolean())
            {
                // Must use distinct to make sure no duplicates:
                typeVars = TestUtil.makeList(r, 1, 4, () -> new Pair<>(TypeVariableKind.TYPE, "" + r.nextChar('a', 'z'))).stream().distinct().collect(ImmutableList.toImmutableList());
            }
            else
            {
                typeVars = ImmutableList.of();
            }
            // Outside type variables are not visible in a new tagged type:
            boolean noInner = r.nextInt() % 3 == 1;
            ArrayList<@Nullable DataType> types = noInner ? new ArrayList<@Nullable DataType>() : new ArrayList<@Nullable DataType>(TestUtil.makeList(r, 1, 10, () -> genDataType.generate(r, status).dataType));
            int extraNulls = r.nextInt(5);
            for (int i = 0; i < extraNulls; i++)
            {
                types.add(r.nextInt(types.size() + 1), null);
            }
            return new TaggedTypeDefinition(new TypeId("" + r.nextChar('A', 'Z') + r.nextChar('A', 'Z')), typeVars, Utility.mapListExI_Index(types, (i, t) -> new DataType.TagType<JellyType>("T" + i, t == null ? null : JellyType.fromConcrete(t))));
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }
}
