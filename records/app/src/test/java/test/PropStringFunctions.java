package test;

import annotation.qual.Value;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.generator.java.lang.StringGenerator;
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
import records.transformations.function.StringLeft;
import records.transformations.function.StringLength;
import records.transformations.function.StringRight;
import test.gen.GenValueList;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(JUnitQuickcheck.class)
public class PropStringFunctions
{
    private UnitManager mgr;
    {
        try
        {
            mgr = new UnitManager();
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Property
    @OnThread(Tag.Simulation)
    public void propTextLength(@From(StringGenerator.class) String str) throws Throwable
    {
        StringLength function = new StringLength();
        @Nullable Pair<FunctionInstance, DataType> checked = function.typeCheck(Collections.emptyList(), DataType.TEXT, s -> {}, mgr);
        if (checked == null)
        {
            fail("Type check failure");
        }
        else
        {
            assertEquals(DataType.NUMBER, checked.getSecond());
            @Value Number actual = (Number)checked.getFirst().getValue(0, DataTypeUtility.value(str));
            assertEquals(str.codePointCount(0, str.length()), actual.intValue());
            assertTrue(actual.doubleValue() == (double)actual.intValue());
        }
    }

    @Property
    @OnThread(Tag.Simulation)
    public void propLeft(@From(StringGenerator.class) String str) throws Throwable
    {
        StringLeft function = new StringLeft();
        @Nullable Pair<FunctionInstance, DataType> checked = function.typeCheck(Collections.emptyList(), DataType.tuple(DataType.TEXT, DataType.NUMBER), s -> {}, mgr);
        if (checked == null)
        {
            fail("Type check failure");
        }
        else
        {
            assertEquals(DataType.TEXT, checked.getSecond());
            int[] strCodepoints = str.codePoints().toArray();
            // Going beyond length should just return whole string, so go 5 beyond to check:
            for (int i = 0; i <= strCodepoints.length + 5; i++)
            {
                @Value String actual = (String)checked.getFirst().getValue(0, DataTypeUtility.value(new Object[]{str, i}));
                assertTrue(str.startsWith(actual));
                assertEquals(new String(strCodepoints, 0, Math.min(i, strCodepoints.length)), actual);
            }

            @Nullable Pair<FunctionInstance, DataType> checkedFinal = checked;
            TestUtil.assertUserException(() -> checkedFinal.getFirst().getValue(0, DataTypeUtility.value(new Object[]{str, -1})));
        }
    }

    @Property
    @OnThread(Tag.Simulation)
    public void propRight(@From(StringGenerator.class) String str) throws Throwable
    {
        StringRight function = new StringRight();
        @Nullable Pair<FunctionInstance, DataType> checked = function.typeCheck(Collections.emptyList(), DataType.tuple(DataType.TEXT, DataType.NUMBER), s -> {}, mgr);
        if (checked == null)
        {
            fail("Type check failure");
        }
        else
        {
            assertEquals(DataType.TEXT, checked.getSecond());
            int[] strCodepoints = str.codePoints().toArray();
            // Going beyond length should just return whole string, so go 5 beyond to check:
            for (int i = 0; i <= strCodepoints.length + 5; i++)
            {
                @Value String actual = (String)checked.getFirst().getValue(0, DataTypeUtility.value(new Object[]{str, i}));
                assertTrue(str.endsWith(actual));
                assertEquals(new String(strCodepoints, Math.max(0, strCodepoints.length - i), Math.min(i, strCodepoints.length)), actual);
            }

            @Nullable Pair<FunctionInstance, DataType> checkedFinal = checked;
            TestUtil.assertUserException(() -> checkedFinal.getFirst().getValue(0, DataTypeUtility.value(new Object[]{str, -1})));
        }
    }
}
