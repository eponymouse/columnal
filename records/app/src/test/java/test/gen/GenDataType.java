package test.gen;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.datatype.NumberInfo;
import records.data.datatype.TypeManager;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import test.TestUtil;
import utility.ExSupplier;
import utility.Utility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by neil on 13/01/2017.
 */
public class GenDataType extends Generator<DataType>
{

    private final TypeManager typeManager;

    public GenDataType()
    {
        super(DataType.class);
        try
        {
            typeManager = new TypeManager(new UnitManager());
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public DataType generate(SourceOfRandomness r, GenerationStatus generationStatus)
    {
        // Use depth system to prevent infinite generation:
        try
        {
            return genDepth(r, 3, generationStatus);
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }

    private DataType genDepth(SourceOfRandomness r, int maxDepth, GenerationStatus gs) throws UserException, InternalException
    {
        List<ExSupplier<DataType>> options = new ArrayList<>(Arrays.asList(
            () -> DataType.BOOLEAN,
            () -> DataType.TEXT,
            () -> DataType.number(new NumberInfo(new GenUnit().generate(r, gs), null /* TODO generate NDI? */)),
            () -> DataType.date(new DateTimeInfo(r.choose(DateTimeType.values())))
        ));
        if (maxDepth > 1)
        {
            options.addAll(Arrays.asList(
                () -> DataType.tuple(TestUtil.makeList(r, 2, 12, () -> genDepth(r, maxDepth - 1, gs))),
                () -> DataType.array(genDepth(r, maxDepth - 1, gs)),
                () -> genTagged(r, maxDepth, gs)
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
        public DataType generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
        {
            try
            {
                return genTagged(sourceOfRandomness, 3, generationStatus);
            }
            catch (InternalException | UserException e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    protected DataType genTagged(SourceOfRandomness r, int maxDepth, GenerationStatus gs) throws InternalException, UserException
    {
        ArrayList<@Nullable DataType> types = new ArrayList<>(TestUtil.makeList(r, 1, 10, () -> genDepth(r, maxDepth - 1, gs)));
        int extraNulls = r.nextInt(5);
        for (int i = 0; i < extraNulls; i++)
        {
            types.add(r.nextInt(types.size() + 1), null);
        }
        return typeManager.registerTaggedType("" + r.nextChar('A', 'Z') + r.nextChar('A', 'Z'), Utility.mapListExI_Index(types, (i, t) -> new DataType.TagType<DataType>("T" + i, t)));
    }
}
