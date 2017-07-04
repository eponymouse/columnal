package test;

import annotation.qual.Value;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.Before;
import org.junit.runner.RunWith;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.function.Count;
import records.transformations.function.FunctionInstance;
import records.transformations.function.GetElement;
import test.gen.GenValueList;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;

import java.util.Arrays;
import java.util.Collections;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * Created by neil on 13/12/2016.
 */
@RunWith(JUnitQuickcheck.class)
public class PropListFunctions
{
    private @MonotonicNonNull UnitManager mgr;
    @Before
    public void init() throws UserException, InternalException
    {
        mgr = new UnitManager();
    }


    @Property
    @OnThread(Tag.Simulation)
    public void propCount(@From(GenValueList.class) GenValueList.ListAndType src) throws Throwable
    {
        if (mgr == null)
            throw new RuntimeException();
        Count function = new Count();
        @Nullable Pair<FunctionInstance, DataType> checked = function.typeCheck(Collections.emptyList(), src.type, s -> {}, mgr);
        if (checked == null)
        {
            fail("Type check failure");
        }
        else
        {
            assertEquals(DataType.NUMBER, checked.getSecond());
            @Value Object actual = checked.getFirst().getValue(0, DataTypeUtility.value(src.list));
            assertEquals(src.list.size(), actual);
        }
    }

    @Property
    @OnThread(Tag.Simulation)
    public void propElement(@From(GenValueList.class) GenValueList.ListAndType src) throws Throwable
    {
        if (mgr == null)
            throw new RuntimeException();
        GetElement function = new GetElement();
        @Nullable Pair<FunctionInstance, DataType> checked = function.typeCheck(Collections.emptyList(), DataType.tuple(src.type, DataType.NUMBER), s -> {}, mgr);
        if (checked == null)
        {
            // It's ok to fail on empty array type; that's expected:
            if (!src.type.isArray() || !src.type.getMemberType().isEmpty())
                fail("Type check failure");
        }
        else
        {
            assertEquals(src.type.getMemberType().get(0), checked.getSecond());
            // Try valid values:
            for (int i = 0; i < src.list.size(); i++)
            {
                @Value Object actual = checked.getFirst().getValue(0, DataTypeUtility.value(new @Value Object[]{DataTypeUtility.value(src.list), DataTypeUtility.<Integer>value(i + 1 /* one-based index */)}));
                assertSame(src.list.get(i), actual);
            }
            // Try invalid integer values:
            for (int i : Arrays.asList(-2, -1, 0, src.list.size() + 1, src.list.size() + 2))
            {
                try
                {
                    checked.getFirst().getValue(0, DataTypeUtility.value(new @Value Object[]{DataTypeUtility.value(src.list), DataTypeUtility.<Integer>value(i)}));
                    fail("Expected user exception for index " + i);
                }
                catch (UserException e)
                {
                }
            }
            // Try invalid non-integer values:
        }
    }
}
