package test.gen;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import records.data.ColumnId;
import records.data.Table.InitialLoadDetails;
import records.data.TableId;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.Concatenate;
import test.DummyManager;
import test.TestUtil;
import test.TestUtil.Transformation_Mgr;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assume.assumeNoException;

/**
 * Created by neil on 02/02/2017.
 */
public class GenNonsenseConcatenate extends GenValueBase<Transformation_Mgr>
{
    public GenNonsenseConcatenate()
    {
        super(Transformation_Mgr.class);
    }

    @Override
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public Transformation_Mgr generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
    {
        this.r = sourceOfRandomness;
        this.gs = generationStatus;

        DummyManager mgr = TestUtil.managerWithTestTypes();

        TableId ourId = TestUtil.generateTableId(sourceOfRandomness);
        ImmutableList<TableId> srcIds = TestUtil.makeList(sourceOfRandomness, 1, 5, () -> TestUtil.generateTableId(sourceOfRandomness));

        int numMissingCols = sourceOfRandomness.nextInt(0, 10);
        GenValueAnyType genValue = new GenValueAnyType();
        Map<ColumnId, Pair<DataType, Optional<@Value Object>>> missing = new HashMap<>();
        for (int i = 0; i < numMissingCols; i++)
        {
            // Although it's nonsense, type must match the generated value or else load will fail:
            DataType type = sourceOfRandomness.choose(TestUtil.distinctTypes);
            try
            {
                missing.put(TestUtil.generateColumnId(sourceOfRandomness), new Pair<DataType, Optional<@Value Object>>(type, sourceOfRandomness.nextBoolean() ? Optional.<@Value Object>empty() : Optional.<@Value Object>of(makeValue(type))));
            }
            catch (UserException | InternalException e)
            {
                throw new RuntimeException(e);
            }
        }

        try
        {
            return new Transformation_Mgr(mgr, new Concatenate(mgr, new InitialLoadDetails(ourId, null, null), srcIds, missing));
        }
        catch (InternalException e)
        {
            assumeNoException(e);
            throw new RuntimeException(e);
        }
    }

}
