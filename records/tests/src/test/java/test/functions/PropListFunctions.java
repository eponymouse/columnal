package test.functions;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.runner.RunWith;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.error.UserException;
import records.transformations.function.list.Count;
import records.transformations.function.FunctionDefinition;
import records.transformations.function.list.GetElement;
import records.transformations.function.Max;
import records.transformations.function.Min;
import test.TestUtil;
import test.gen.GenRandom;
import test.gen.GenValueList;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;
import records.transformations.expression.function.ValueFunction;

import java.util.Arrays;
import java.util.Random;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.*;

/**
 * Created by neil on 13/12/2016.
 */
@RunWith(JUnitQuickcheck.class)
public class PropListFunctions
{
    @Property
    @OnThread(Tag.Simulation)
    public void propMinMax(@From(GenValueList.class) GenValueList.ListAndType src) throws Throwable
    {
        if (src.type.isArray() && src.type.getMemberType().isEmpty())
            return; // Ignore blank array type

        FunctionDefinition minFunction = new Min();
        FunctionDefinition maxFunction = new Max();
        @Nullable Pair<ValueFunction, DataType> minChecked = TestUtil.typeCheckFunction(minFunction, ImmutableList.of(src.type));
        @Nullable Pair<ValueFunction, DataType> maxChecked = TestUtil.typeCheckFunction(maxFunction, ImmutableList.of(src.type));

        if (minChecked == null || maxChecked == null)
        {
            fail("Type check failure: " + minChecked + " " + maxChecked + " for type" + src.type);
        }
        else if (src.list.size() == 0)
        {
            @NonNull Pair<ValueFunction, DataType> minFinal = minChecked;
            @NonNull Pair<ValueFunction, DataType> maxFinal = maxChecked;
            TestUtil.assertUserException(() -> minFinal.getFirst().call(new @Value Object[] {DataTypeUtility.value(src.list)}));
            TestUtil.assertUserException(() -> maxFinal.getFirst().call(new @Value Object[] {DataTypeUtility.value(src.list)}));
        }
        else
        {
            @Value Object expectedMin = src.list.get(0);
            @Value Object expectedMax = src.list.get(0);
            for (int i = 1; i < src.list.size(); i++)
            {
                @Value Object x = src.list.get(i);
                if (Utility.compareValues(x, expectedMin) < 0)
                    expectedMin = x;
                if (Utility.compareValues(x, expectedMax) > 0)
                    expectedMax = x;
            }

            assertEquals(src.type.getMemberType().get(0), minChecked.getSecond());
            assertEquals(src.type.getMemberType().get(0), maxChecked.getSecond());
            @Value Object minActual = minChecked.getFirst().call(new @Value Object[] {DataTypeUtility.value(src.list)});
            @Value Object maxActual = maxChecked.getFirst().call(new @Value Object[] {DataTypeUtility.value(src.list)});
            TestUtil.assertValueEqual("", expectedMin, minActual);
            TestUtil.assertValueEqual("", expectedMax, maxActual);
        }
    }


    @Property
    @OnThread(Tag.Simulation)
    public void propCount(@From(GenValueList.class) GenValueList.ListAndType src) throws Throwable
    {
        Count function = new Count();
        @Nullable Pair<ValueFunction, DataType> checked = TestUtil.typeCheckFunction(function, ImmutableList.of(src.type));
        if (checked == null)
        {
            fail("Type check failure");
        }
        else
        {
            assertEquals(DataType.NUMBER, checked.getSecond());
            @Value Object actual = checked.getFirst().call(new @Value Object[] {DataTypeUtility.value(src.list)});
            assertEquals(src.list.size(), actual);
        }
    }

    @Property
    @OnThread(Tag.Simulation)
    public void propElement(@From(GenValueList.class) GenValueList.ListAndType src) throws Throwable
    {
        GetElement function = new GetElement();
        @Nullable Pair<ValueFunction, DataType> checked = null;
        try
        {
            checked = TestUtil.typeCheckFunction(function, ImmutableList.of(src.type, DataType.NUMBER));
        }
        catch (Exception e)
        {
            // This can be ok, if it was empty array,
        }
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
                @Value Object actual = checked.getFirst().call(DataTypeUtility.value(new @Value Object[]{DataTypeUtility.value(src.list), DataTypeUtility.<Integer>value(i + 1 /* one-based index */)}));
                assertSame(src.list.get(i), actual);
            }
            // Try invalid integer values:
            for (int i : Arrays.asList(-2, -1, 0, src.list.size() + 1, src.list.size() + 2))
            {
                try
                {
                    checked.getFirst().call(DataTypeUtility.value(new @Value Object[]{DataTypeUtility.value(src.list), DataTypeUtility.<Integer>value(i)}));
                    fail("Expected user exception for index " + i);
                }
                catch (UserException e)
                {
                }
            }
            // Try invalid non-integer values:
        }
    }

    @Property
    @OnThread(Tag.Simulation)
    public void propAny(@From(GenValueList.class) GenValueList.ListAndType src, @From(GenRandom.class) Random r) throws Throwable
    {
        if (src.list.size() == 0)
        {
            //assertFalse(Utility.cast(TestUtil.runExpression("@call @function any(" + DataTypeUtility.valueToString(src.type, src.list, null, true) + ", (? = ?))"), Boolean.class));
        }
        else
        {
            // Check that random element is found:
            @Value Object elem = src.list.get(r.nextInt(src.list.size()));
            assertTrue("" + src.type, Utility.cast(TestUtil.runExpression("@call @function any(" + DataTypeUtility.valueToString(src.type, src.list, null, true) + ", (? = " + DataTypeUtility.valueToString(src.type.getMemberType().get(0), elem, null, true) + "))"), Boolean.class));
        }
    }
}
