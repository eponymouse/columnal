package test.gen;

import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DataTypeVisitorEx;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.datatype.DataType.TagType;
import records.data.datatype.NumberInfo;
import records.data.datatype.TaggedTypeDefinition;
import records.data.datatype.TypeId;
import records.data.datatype.TypeManager;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import test.TestUtil;
import test.gen.GenDataType.DataTypeAndManager;
import utility.ExSupplier;
import utility.Pair;
import utility.Utility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Created by neil on 13/01/2017.
 */
public class GenDataType extends Generator<DataTypeAndManager>
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
    
    public static class DataTypeAndManager
    {
        public final TypeManager typeManager;
        public final DataType dataType;

        public DataTypeAndManager(TypeManager typeManager, DataType dataType) throws InternalException
        {
            this.typeManager = typeManager;
            this.dataType = dataType;
            TestUtil.assertNoTypeVariables(dataType);
        }
    }

    public GenDataType()
    {
        super(DataTypeAndManager.class);
    }

    @Override
    public DataTypeAndManager generate(SourceOfRandomness r, GenerationStatus generationStatus)
    {
        // Use depth system to prevent infinite generation:
        try
        {
            return new DataTypeAndManager(typeManager, genDepth(r, 3, generationStatus, ImmutableList.of()));
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }

    private DataType genDepth(SourceOfRandomness r, int maxDepth, GenerationStatus gs, ImmutableList<String> availableTypeVariables) throws UserException, InternalException
    {
        List<ExSupplier<DataType>> options = new ArrayList<>(Arrays.asList(
            () -> DataType.BOOLEAN,
            () -> DataType.TEXT,
            () -> DataType.number(new NumberInfo(new GenUnit().generate(r, gs), null /* TODO generate NDI? */)),
            () -> DataType.date(new DateTimeInfo(r.choose(DateTimeType.values())))
        ));
        if (!availableTypeVariables.isEmpty())
        {
            options.add(() -> DataType.typeVariable(r.choose(availableTypeVariables)));
        }
        if (maxDepth > 1)
        {
            options.addAll(Arrays.asList(
                () -> DataType.tuple(TestUtil.makeList(r, 2, 12, () -> genDepth(r, maxDepth - 1, gs, availableTypeVariables))),
                () -> DataType.array(genDepth(r, maxDepth - 1, gs, availableTypeVariables)),
                () -> genTagged(r, maxDepth, gs, availableTypeVariables)
            ));
        }
        return r.<ExSupplier<DataType>>choose(options).get();
    }

    public static class GenTaggedType extends GenDataType
    {
        public GenTaggedType()
        {
        }

        @Override
        public DataTypeAndManager generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
        {
            try
            {
                return new DataTypeAndManager(typeManager, genTagged(sourceOfRandomness, 3, generationStatus, ImmutableList.of()));
            }
            catch (InternalException | UserException e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    protected DataType genTagged(SourceOfRandomness r, int maxDepth, GenerationStatus gs, ImmutableList<String> originalTypeVars) throws InternalException, UserException
    {
        TaggedTypeDefinition typeDefinition;

        // Limit it to 100 types:
        int typeIndex = r.nextInt(100);
        if (typeIndex > typeManager.getKnownTaggedTypes().size())
        {
            // Don't need to add N more, just add one for now:

            final ImmutableList<String> typeVars;
            if (r.nextBoolean())
            {
                int typeVarSize = r.nextInt(1, 4);
                typeVars = ImmutableList.copyOf(TestUtil.makeList(r, 1, 4, () -> "" + r.nextChar('a', 'z')));
            }
            else
            {
                typeVars = ImmutableList.of();
            }
            // Outside type variables are not visible in a new tagged type:
            ArrayList<@Nullable DataType> types = new ArrayList<>(TestUtil.makeList(r, 1, 10, () -> genDepth(r, maxDepth - 1, gs, typeVars)));
            int extraNulls = r.nextInt(5);
            for (int i = 0; i < extraNulls; i++)
            {
                types.add(r.nextInt(types.size() + 1), null);
            }
            typeDefinition = typeManager.registerTaggedType("" + r.nextChar('A', 'Z') + r.nextChar('A', 'Z'), typeVars, Utility.mapListExI_Index(types, (i, t) -> new DataType.TagType<DataType>("T" + i, t)));
        }
        else
        {
            typeDefinition = r.choose(typeManager.getKnownTaggedTypes().values());
        }
        return typeDefinition.instantiate(Utility.mapListExI(typeDefinition.getTypeArguments(), _arg -> genDepth(r, maxDepth - 1, gs, originalTypeVars)));
    }
}
