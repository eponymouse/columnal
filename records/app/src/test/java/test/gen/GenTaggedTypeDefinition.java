package test.gen;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
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
import java.util.stream.Collectors;

import static test.gen.GenJellyType.TypeKinds.BOOLEAN_TUPLE_LIST;
import static test.gen.GenJellyType.TypeKinds.BUILTIN_TAGGED;
import static test.gen.GenJellyType.TypeKinds.NUM_TEXT_TEMPORAL;

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
            final ImmutableList<Pair<TypeVariableKind, String>> typeVars;
            if (r.nextInt(3) == 1)
            {
                // Must use distinct to make sure no duplicates:
                typeVars = TestUtil.makeList(r, 1, 4, () -> new Pair<>(TypeVariableKind.TYPE, "" + r.nextChar('a', 'z'))).stream().distinct().collect(ImmutableList.toImmutableList());
            }
            else
            {
                typeVars = ImmutableList.of();
            }
            GenJellyType genDataType = new GenJellyType(ImmutableSet.of(NUM_TEXT_TEMPORAL,
                    BOOLEAN_TUPLE_LIST,
                    BUILTIN_TAGGED), typeVars.stream().map(p -> p.getSecond()).collect(ImmutableSet.toImmutableSet()));
            
            // Outside type variables are not visible in a new tagged type:
            boolean noInner = r.nextInt() % 3 == 1;
            ArrayList<@Nullable JellyType> types = noInner ? new ArrayList<@Nullable JellyType>() : new ArrayList<@Nullable JellyType>(TestUtil.makeList(r, 1, 10, () -> genDataType.generate(r, status).jellyType));
            int extraNulls = r.nextInt(5) + (types.isEmpty() ? 1 : 0);
            for (int i = 0; i < extraNulls; i++)
            {
                types.add(r.nextInt(types.size() + 1), null);
            }
            @SuppressWarnings("identifier")
            TypeId typeId = new TypeId("" + r.nextChar('A', 'Z') + r.nextChar('A', 'Z'));
            return new TaggedTypeDefinition(typeId, typeVars, Utility.mapListExI_Index(types, (i, t) -> new DataType.TagType<JellyType>("T" + i, t)));
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }
}
