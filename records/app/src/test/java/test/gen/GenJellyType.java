package test.gen;

import annotation.identifier.qual.ExpressionIdentifier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.datatype.TaggedTypeDefinition;
import records.data.datatype.TaggedTypeDefinition.TypeVariableKind;
import records.data.datatype.TypeManager;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.jellytype.JellyType;
import records.jellytype.JellyUnit;
import test.TestUtil;
import test.gen.GenJellyType.JellyTypeAndManager;
import utility.Either;
import utility.ExSupplier;
import utility.Pair;
import utility.Utility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Created by neil on 13/01/2017.
 */
public class GenJellyType extends Generator<JellyTypeAndManager>
{
    // This isn't ideal because it makes tests inter-dependent:
    protected static final TypeManager typeManager;
    static {
        try
        {
            typeManager = new TypeManager(new UnitManager());
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }

    public static enum TypeKinds {
        NUM_TEXT_TEMPORAL,
        BOOLEAN_TUPLE_LIST,
        BUILTIN_TAGGED,
        NEW_TAGGED
    }

    private final ImmutableSet<TypeKinds> typeKinds;
    private final ImmutableSet<@ExpressionIdentifier String> availableTypeVars;
    private final boolean mustHaveValues;

    public static class JellyTypeAndManager
    {
        public final TypeManager typeManager;
        public final JellyType jellyType;

        public JellyTypeAndManager(TypeManager typeManager, JellyType jellyType) throws InternalException
        {
            this.typeManager = typeManager;
            this.jellyType = jellyType;
        }
    }
    
    public GenJellyType()
    {
        this(ImmutableSet.copyOf(TypeKinds.values()));
    }

    public GenJellyType(ImmutableSet<TypeKinds> typeKinds)
    {
        this(typeKinds, ImmutableSet.of(), false);
    }

    public GenJellyType(ImmutableSet<TypeKinds> typeKinds, ImmutableSet<@ExpressionIdentifier String> availableTypeVars, boolean mustHaveValues)
    {
        super(JellyTypeAndManager.class);
        this.typeKinds = typeKinds;
        this.availableTypeVars = availableTypeVars;
        this.mustHaveValues = mustHaveValues;
    }

    @Override
    public JellyTypeAndManager generate(SourceOfRandomness r, GenerationStatus generationStatus)
    {
        // Use depth system to prevent infinite generation:
        try
        {
            return new JellyTypeAndManager(typeManager, genDepth(r, 3, generationStatus));
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }

    private JellyType genDepth(SourceOfRandomness r, int maxDepth, GenerationStatus gs) throws UserException, InternalException
    {
        List<ExSupplier<JellyType>> options = new ArrayList<>();
        if (typeKinds.contains(TypeKinds.NUM_TEXT_TEMPORAL))
        {
            options.addAll(Arrays.asList(
                () -> JellyType.fromConcrete(DataType.TEXT),
                () -> JellyType.number(JellyUnit.fromConcrete(new GenUnit().generate(r, gs))),
                () -> JellyType.fromConcrete(DataType.date(new DateTimeInfo(r.choose(DateTimeType.values()))))
            ));
        }
        if (typeKinds.contains(TypeKinds.BOOLEAN_TUPLE_LIST))
        {
            options.add(() -> JellyType.fromConcrete(DataType.BOOLEAN));

            if (maxDepth > 1)
            {
                options.addAll(Arrays.asList(
                        () -> JellyType.tuple(TestUtil.makeList(r, 2, 5, () -> genDepth(r, maxDepth - 1, gs))),
                        () -> JellyType.list(genDepth(r, maxDepth - 1, gs))
                ));
            }
        }
        if ((typeKinds.contains(TypeKinds.BUILTIN_TAGGED) || typeKinds.contains(TypeKinds.NEW_TAGGED)) && maxDepth > 1)
            options.add(() -> genTagged(r, maxDepth, gs));
        
        if (!availableTypeVars.isEmpty())
            options.add(() -> JellyType.typeVariable(r.choose(availableTypeVars)));
        
        return r.<ExSupplier<JellyType>>choose(options).get();
    }

    public static class GenTaggedType extends GenJellyType
    {
        public GenTaggedType()
        {
        }

        @Override
        public JellyTypeAndManager generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
        {
            try
            {
                return new JellyTypeAndManager(typeManager, genTagged(sourceOfRandomness, 3, generationStatus));
            }
            catch (InternalException | UserException e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    protected JellyType genTagged(SourceOfRandomness r, int maxDepth, GenerationStatus gs) throws InternalException, UserException
    {
        TaggedTypeDefinition typeDefinition = null;
        List<TaggedTypeDefinition> pool;
        if (typeKinds.contains(TypeKinds.BUILTIN_TAGGED))
            pool = new ArrayList<>(typeManager.getKnownTaggedTypes().values());
        else
            pool = new ArrayList<>(typeManager.getUserTaggedTypes().values());
        
        if (mustHaveValues)
        {
            pool.removeIf(t -> t.getTags().isEmpty());
        }

        // Limit it to 100 types:
        int typeIndex = r.nextInt(100);
        if (typeIndex >= pool.size() && typeKinds.contains(TypeKinds.NEW_TAGGED))
        {
            // Don't need to add N more, just add one for now:

            final ImmutableList<Pair<TypeVariableKind, @ExpressionIdentifier String>> typeVars;
            if (r.nextBoolean())
            {
                // Must use distinct to make sure no duplicates:
                typeVars = TestUtil.makeList(r, 1, 4, () -> new Pair<>(r.nextInt(3) == 1 ? TypeVariableKind.UNIT : TypeVariableKind.TYPE, "" + r.nextChar('a', 'z'))).stream().distinct().collect(ImmutableList.toImmutableList());
            }
            else
            {
                typeVars = ImmutableList.of();
            }
            // Outside type variables are not visible in a new tagged type:
            ArrayList<@Nullable JellyType> types;
            // First add the items with inner type:
            if (r.nextInt(3) == 1)
                types = new ArrayList<>();
            else
                types = new ArrayList<>(TestUtil.makeList(r, 1, 10, () -> genDepth(r, maxDepth - 1, gs)));
            // Then those with inner types, making sure we have at least one:
            int extraNulls = r.nextInt(5) + (types.isEmpty() ? 1 : 0);
            for (int i = 0; i < extraNulls; i++)
            {
                types.add(r.nextInt(types.size() + 1), null);
            }
            @SuppressWarnings("identifier")
            @ExpressionIdentifier String typeName = "" + r.nextChar('A', 'Z') + r.nextChar('A', 'Z');
            typeDefinition = typeManager.registerTaggedType(typeName, typeVars, Utility.mapListExI_Index(types, (i, t) -> {
                @SuppressWarnings("identifier")
                @ExpressionIdentifier String tagName = "T" + i;
                return new DataType.TagType<JellyType>(tagName, t);
            }));
        }
        
        if (typeDefinition == null)
        {
            typeDefinition = r.choose(pool);
        }
        return JellyType.tagged(typeDefinition.getTaggedTypeName(), Utility.mapListExI(typeDefinition.getTypeArguments(), (Pair<TypeVariableKind, String> arg) -> {
            if (arg.getFirst() == TypeVariableKind.TYPE)
                return Either.right(genDepth(r, maxDepth - 1, gs));
            else
                return Either.left(JellyUnit.fromConcrete(new GenUnit().generate(r, gs)));
        }));
    }
}
