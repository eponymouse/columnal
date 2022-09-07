package test.gen.nonsenseTrans;

import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import xyz.columnal.data.ColumnId;
import xyz.columnal.data.DataTestUtil;
import xyz.columnal.data.Table.InitialLoadDetails;
import xyz.columnal.data.TableId;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.HideColumns;
import test.DummyManager;
import test.TestUtil;
import test.TestUtil.Transformation_Mgr;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Pair;

import java.util.List;

import static org.junit.Assume.assumeNoException;

/**
 * Created by neil on 02/02/2017.
 */
public class GenNonsenseHideColumns extends Generator<Transformation_Mgr>
{
    public GenNonsenseHideColumns()
    {
        super(Transformation_Mgr.class);
    }

    @Override
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public Transformation_Mgr generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
    {
        Pair<TableId, TableId> ids = TestUtil.generateTableIdPair(sourceOfRandomness);
        List<ColumnId> cols = DataTestUtil.makeList(sourceOfRandomness, 0, 10, () -> TestUtil.generateColumnId(sourceOfRandomness));

        try
        {
            DummyManager mgr = new DummyManager();
            return new Transformation_Mgr(mgr, new HideColumns(mgr, new InitialLoadDetails(ids.getFirst(), null, null, null), ids.getSecond(), ImmutableList.copyOf(cols)));
        }
        catch (InternalException | UserException e)
        {
            assumeNoException(e);
            throw new RuntimeException(e);
        }
    }
}
