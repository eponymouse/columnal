/*
 * Columnal: Safer, smoother data table processing.
 * Copyright (c) Neil Brown, 2016-2020, 2022.
 *
 * This file is part of Columnal.
 *
 * Columnal is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Columnal is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for 
 * more details.
 *
 * You should have received a copy of the GNU General Public License along 
 * with Columnal. If not, see <https://www.gnu.org/licenses/>.
 */

package test.functions;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.runner.RunWith;
import xyz.columnal.data.TBasicUtil;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataType.SpecificDataTypeVisitor;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.function.list.Count;
import xyz.columnal.transformations.function.FunctionDefinition;
import xyz.columnal.transformations.function.list.GetElement;
import xyz.columnal.transformations.function.comparison.Max;
import xyz.columnal.transformations.function.comparison.Min;
import test.gen.GenRandom;
import test.gen.GenValueList;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.Utility;
import xyz.columnal.transformations.expression.function.ValueFunction;

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
        FunctionDefinition minFunction = new Min();
        FunctionDefinition maxFunction = new Max();
        @Nullable Pair<ValueFunction, DataType> minChecked = TFunctionUtil.typeCheckFunction(minFunction, ImmutableList.of(src.type));
        @Nullable Pair<ValueFunction, DataType> maxChecked = TFunctionUtil.typeCheckFunction(maxFunction, ImmutableList.of(src.type));

        if (minChecked == null || maxChecked == null)
        {
            fail("Type check failure: " + minChecked + " " + maxChecked + " for type" + src.type);
        }
        else if (src.list.size() == 0)
        {
            @NonNull Pair<ValueFunction, DataType> minFinal = minChecked;
            @NonNull Pair<ValueFunction, DataType> maxFinal = maxChecked;
            TBasicUtil.assertUserException(() -> minFinal.getFirst().call(new @Value Object[] {DataTypeUtility.value(src.list)}));
            TBasicUtil.assertUserException(() -> maxFinal.getFirst().call(new @Value Object[] {DataTypeUtility.value(src.list)}));
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

            assertEquals(getInnerType(src.type), minChecked.getSecond());
            assertEquals(getInnerType(src.type), maxChecked.getSecond());
            @Value Object minActual = minChecked.getFirst().call(new @Value Object[] {DataTypeUtility.value(src.list)});
            @Value Object maxActual = maxChecked.getFirst().call(new @Value Object[] {DataTypeUtility.value(src.list)});
            TBasicUtil.assertValueEqual("", expectedMin, minActual);
            TBasicUtil.assertValueEqual("", expectedMax, maxActual);
        }
    }


    @Property
    @OnThread(Tag.Simulation)
    public void propCount(@From(GenValueList.class) GenValueList.ListAndType src) throws Throwable
    {
        Count function = new Count();
        @Nullable Pair<ValueFunction, DataType> checked = TFunctionUtil.typeCheckFunction(function, ImmutableList.of(src.type));
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
            checked = TFunctionUtil.typeCheckFunction(function, ImmutableList.of(src.type, DataType.NUMBER));
        }
        catch (Exception e)
        {
            // This can be ok, if it was empty array,
        }
        if (checked == null)
        {
            fail("Type check failure");
        }
        else
        {
            assertEquals(getInnerType(src.type), checked.getSecond());
            // Try valid values:
            for (int i = 0; i < src.list.size(); i++)
            {
                @Value Object actual = checked.getFirst().call(new @Value Object[]{DataTypeUtility.value(src.list), DataTypeUtility.<Integer>value(i + 1 /* one-based index */)});
                assertSame(src.list.get(i), actual);
            }
            // Try invalid integer values:
            for (int i : Arrays.asList(-2, -1, 0, src.list.size() + 1, src.list.size() + 2))
            {
                try
                {
                    checked.getFirst().call(new @Value Object[]{DataTypeUtility.value(src.list), DataTypeUtility.<Integer>value(i)});
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
            //assertFalse(Utility.cast(TestUtil.runExpression("@call function\\\\any(" + DataTypeUtility.valueToString(src.type, src.list, null, true) + ", (? = ?))"), Boolean.class));
        }
        else
        {
            // Check that random element is found:
            @Value Object elem = src.list.get(r.nextInt(src.list.size()));
            assertTrue("" + src.type, Utility.cast(TFunctionUtil.runExpression("@call function\\\\any(" + DataTypeUtility.valueToString(src.list, src.type, false, null) + ", (? = " + DataTypeUtility.valueToString(elem, getInnerType(src.type), false, null) + "))"), Boolean.class));
        }
    }

    private DataType getInnerType(DataType listType) throws InternalException
    {
        return listType.apply(new SpecificDataTypeVisitor<DataType>() {
            @Override
            public DataType array(DataType inner) throws InternalException
            {
                return inner;
            }
        });
    }
}
