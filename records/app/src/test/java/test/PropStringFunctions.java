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
            for (int i = 0; i <= strCodepoints.length; i++)
            {
                @Value String actual = (String)checked.getFirst().getValue(0, DataTypeUtility.value(new Object[]{str, i}));
                assertTrue(str.startsWith(actual));
                assertEquals(new String(strCodepoints, 0, i), actual);
            }

        }
    }
}
