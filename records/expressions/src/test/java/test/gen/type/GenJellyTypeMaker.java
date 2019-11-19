package test.gen.type;

import annotation.identifier.qual.ExpressionIdentifier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.com.google.common.collect.Sets;
import records.data.DataTestUtil;
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
import records.jellytype.JellyTypeRecord.Field;
import records.jellytype.JellyUnit;
import test.gen.GenUnit;
import test.gen.type.GenJellyTypeMaker.JellyTypeMaker;
import utility.Either;
import utility.ExSupplier;
import utility.Pair;
import utility.Utility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Generates a generator for jelly types
 */
@SuppressWarnings("recorded")
public class GenJellyTypeMaker extends Generator<JellyTypeMaker>
{
    public static enum TypeKinds {
        NUM_TEXT_TEMPORAL,
        BOOLEAN,
        RECORD_LIST,
        MAYBE_UNNESTED,
        OTHER_BUILTIN_TAGGED, // besides maybe
        NEW_TAGGED_NO_INNER,
        NEW_TAGGED_INNER,
    }

    private final ImmutableSet<TypeKinds> startingTypeKinds;
    private final ImmutableSet<@ExpressionIdentifier String> availableTypeVars;
    private final boolean mustHaveValues;

    /**
     * A generator for jelly types, each of which is added to the same type manager.
     */
    public class JellyTypeMaker
    {
        public final TypeManager typeManager;
        private final ExSupplier<JellyType> maker;

        private JellyTypeMaker(TypeManager typeManager, ExSupplier<JellyType> maker)
        {
            this.typeManager = typeManager;
            this.maker = maker;
        }
        
        public JellyType makeType()
        {
            try
            {
                return maker.get();
            }
            catch (InternalException | UserException e)
            {
                throw new RuntimeException(e);
            }
        }
    }
    
    public GenJellyTypeMaker()
    {
        this(ImmutableSet.copyOf(TypeKinds.values()));
    }

    public GenJellyTypeMaker(ImmutableSet<TypeKinds> typeKinds)
    {
        this(typeKinds, ImmutableSet.of(), false);
    }

    public GenJellyTypeMaker(ImmutableSet<TypeKinds> typeKinds, ImmutableSet<@ExpressionIdentifier String> availableTypeVars, boolean mustHaveValues)
    {
        super(JellyTypeMaker.class);
        this.startingTypeKinds = typeKinds;
        this.availableTypeVars = availableTypeVars;
        this.mustHaveValues = mustHaveValues;
    }

    @Override
    public JellyTypeMaker generate(SourceOfRandomness r, GenerationStatus generationStatus)
    {
        // Use depth system to prevent infinite generation:
        try
        {
            /*
                @Nullable InputStream stream = ResourceUtility.getResourceAsStream("builtin_units.txt");
                if (stream == null)
                    return new UnitManager(null);
                else */
            TypeManager typeManager = new TypeManager(new UnitManager());
            return new JellyTypeMaker(typeManager, () -> genDepth(typeManager, r, 3, generationStatus, startingTypeKinds));
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }

    private JellyType genDepth(TypeManager typeManager, SourceOfRandomness r, int maxDepth, GenerationStatus gs, ImmutableSet<TypeKinds> typeKinds) throws UserException, InternalException
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
        if (typeKinds.contains(TypeKinds.BOOLEAN))
        {
            options.add(() -> JellyType.fromConcrete(DataType.BOOLEAN));
        }
        if (maxDepth > 1 && typeKinds.contains(TypeKinds.RECORD_LIST))
        {
            options.addAll(Arrays.asList(
                    () -> JellyType.record(DataTestUtil.<Pair<@ExpressionIdentifier String, JellyType>>makeList(r, 2, 5, () -> new Pair<>(DataTestUtil.generateExpressionIdentifier(r), genDepth(typeManager, r, maxDepth - 1, gs, typeKinds))).stream().collect(ImmutableMap.<Pair<@ExpressionIdentifier String, JellyType>, @ExpressionIdentifier String, Field>toImmutableMap((Pair<@ExpressionIdentifier String, JellyType> p) -> p.getFirst(), p -> new Field(p.getSecond(), true), (Field a, Field b) -> a))),
                    () -> JellyType.list(genDepth(typeManager, r, maxDepth - 1, gs, typeKinds))
            ));
        }
        if ((typeKinds.contains(TypeKinds.OTHER_BUILTIN_TAGGED) || typeKinds.contains(TypeKinds.NEW_TAGGED_INNER) || typeKinds.contains(TypeKinds.NEW_TAGGED_NO_INNER) || typeKinds.contains(TypeKinds.MAYBE_UNNESTED)) && maxDepth > 1)
            options.add(() -> genTagged(typeManager, r, maxDepth, gs, typeKinds));
        
        if (!availableTypeVars.isEmpty())
            options.add(() -> JellyType.typeVariable(r.choose(availableTypeVars)));
        
        return r.<ExSupplier<JellyType>>choose(options).get();
    }

    public static class GenTaggedType extends GenJellyTypeMaker
    {
        public GenTaggedType()
        {
        }

        @Override
        public JellyTypeMaker generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
        {
            try
            {
                TypeManager typeManager = new TypeManager(new UnitManager());
                return new JellyTypeMaker(typeManager, () -> genTagged(typeManager, sourceOfRandomness, 3, generationStatus, ImmutableSet.copyOf(TypeKinds.values())));
            }
            catch (InternalException | UserException e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    protected JellyType genTagged(TypeManager typeManager, SourceOfRandomness r, int maxDepth, GenerationStatus gs, ImmutableSet<TypeKinds> typeKinds) throws InternalException, UserException
    {
        TaggedTypeDefinition typeDefinition = null;
        List<TaggedTypeDefinition> pool = new ArrayList<>();
        if (typeKinds.contains(TypeKinds.OTHER_BUILTIN_TAGGED))
            pool.addAll(typeManager.getKnownTaggedTypes().values());
        else if (typeKinds.contains(TypeKinds.MAYBE_UNNESTED) || typeKinds.contains(TypeKinds.OTHER_BUILTIN_TAGGED))
            pool.add(typeManager.getMaybeType());
        
        pool.addAll(typeManager.getUserTaggedTypes().values());
        
        if (mustHaveValues)
        {
            pool.removeIf(t -> t.getTags().isEmpty());
        }

        // Limit it to 100 types:
        int typeIndex = r.nextInt(100);
        if (typeIndex >= pool.size() && (typeKinds.contains(TypeKinds.NEW_TAGGED_INNER) || typeKinds.contains(TypeKinds.NEW_TAGGED_NO_INNER)))
        {
            // Don't need to add N more, just add one for now:

            final ImmutableList<Pair<TypeVariableKind, @ExpressionIdentifier String>> typeVars;
            if (r.nextBoolean() && typeKinds.contains(TypeKinds.NEW_TAGGED_INNER))
            {
                // Must use distinct to make sure no duplicates:
                typeVars = DataTestUtil.<@ExpressionIdentifier String>makeList(r, 1, 4, () -> {
                    @SuppressWarnings("identifier")
                    @ExpressionIdentifier String az = "" + r.nextChar('a', 'z');
                    return az;
                }).stream().distinct().<Pair<TypeVariableKind, @ExpressionIdentifier String>>map(s -> new Pair<TypeVariableKind, @ExpressionIdentifier String>(r.nextInt(3) == 1 ? TypeVariableKind.UNIT : TypeVariableKind.TYPE, s)).collect(ImmutableList.<Pair<TypeVariableKind, @ExpressionIdentifier String>>toImmutableList());
            }
            else
            {
                typeVars = ImmutableList.of();
            }
            // Outside type variables are not visible in a new tagged type:
            ArrayList<@Nullable JellyType> types;
            // First add the items with inner type:
            if (r.nextInt(3) == 1 || !typeKinds.contains(TypeKinds.NEW_TAGGED_INNER))
                types = new ArrayList<>();
            else
                types = new ArrayList<>(DataTestUtil.makeList(r, 1, 10, () -> genDepth(typeManager, r, maxDepth - 1, gs, typeKinds)));
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
                @ExpressionIdentifier String tagName = "T" + r.nextChar('A', 'Z') + " " + i;
                return new DataType.TagType<JellyType>(tagName, t);
            }));
        }
        
        if (typeDefinition == null)
        {
            typeDefinition = r.choose(pool);
        }
        return JellyType.tagged(typeDefinition.getTaggedTypeName(), Utility.mapListExI(typeDefinition.getTypeArguments(), (Pair<TypeVariableKind, String> arg) -> {
            if (arg.getFirst() == TypeVariableKind.TYPE)
                return Either.right(genDepth(typeManager, r, maxDepth - 1, gs, ImmutableSet.<TypeKinds>copyOf(Sets.<TypeKinds>difference(typeKinds, ImmutableSet.<TypeKinds>of(TypeKinds.MAYBE_UNNESTED)))));
            else
                return Either.left(JellyUnit.fromConcrete(new GenUnit().generate(r, gs)));
        }));
    }
}
