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
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.runner.RunWith;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.NumberInfo;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.data.unit.UnitManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.function.Absolute;
import xyz.columnal.transformations.function.FunctionDefinition;
import xyz.columnal.transformations.function.Mean;
import xyz.columnal.transformations.function.number.Round;
import xyz.columnal.transformations.function.Sum;
import test.gen.GenNumber;
import test.gen.GenNumbers;
import test.gen.GenUnit;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.Utility;
import xyz.columnal.transformations.expression.function.ValueFunction;

import java.math.BigDecimal;
import java.util.List;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

/**
 * Created by neil on 13/12/2016.
 */
@RunWith(JUnitQuickcheck.class)
public class PropNumericFunctions
{
    private @MonotonicNonNull UnitManager mgr;
    @Before
    public void init() throws UserException, InternalException
    {
        mgr = new UnitManager();
    }


    @Property
    @OnThread(Tag.Simulation)
    public void propAbs(@From(GenNumber.class) @Value Number src, @From(GenUnit.class) Unit u) throws Throwable
    {
        BigDecimal absed = Utility.toBigDecimal(runNumericFunction(u.toString(), src, u.toString(), new Absolute()));
        // Change *after* call; important so that we test diff classes above, but need BigDecimal for comparisons:
        src = Utility.toBigDecimal(src);
        assertThat(absed, Matchers.greaterThanOrEqualTo(BigDecimal.ZERO));
        assertThat(absed, Matchers.anyOf(Matchers.equalTo(src), Matchers.equalTo(Utility.addSubtractNumbers(DataTypeUtility.value(0), src, false))));
    }

    @Property
    @OnThread(Tag.Simulation)
    public void propRound(@From(GenNumber.class) @Value Number src, @From(GenUnit.class) Unit u) throws Throwable
    {
        @Value BigDecimal rounded = Utility.toBigDecimal(runNumericFunction(u.toString(), src, u.toString(), new Round()));
        @Value BigDecimal orig = Utility.toBigDecimal(src);
        // Check that it is round by doing this; will throw if not:
        try
        {
            rounded.toBigIntegerExact();
        }
        catch (ArithmeticException e)
        {
            org.junit.Assert.fail("Expected integer but was " + rounded + " : " + e.getLocalizedMessage());
        }
        BigDecimal gap = orig.subtract(rounded).abs();
        assertThat(gap, Matchers.lessThanOrEqualTo(BigDecimal.valueOf(0.5)));
    }

    @Property
    @OnThread(Tag.Simulation)
    public void propSum(@From(GenNumbers.class) List<@Value Number> src, @From(GenUnit.class) Unit u) throws Throwable
    {
        @Nullable @Value Number total = src.stream().reduce((a, b) -> Utility.addSubtractNumbers(a, b, true)).orElse(null);
        assertEquals(total == null ? BigDecimal.ZERO : Utility.toBigDecimal(total), Utility.toBigDecimal(runNumericSummaryFunction(u.toString(), src, u.toString(), new Sum())));
    }

    @Property
    @OnThread(Tag.Simulation)
    public void propAverage(@From(GenNumbers.class) List<@Value Number> src, @From(GenUnit.class) Unit u) throws Throwable
    {
        @Nullable @Value Number total = src.stream().reduce((a, b) -> Utility.addSubtractNumbers(a, b, true)).orElse(null);
        if (total == null)
        {
            // List must be empty:
            try
            {
                runNumericSummaryFunction(u.toString(), src, u.toString(), new Mean());
                fail("Should have thrown exception on empty list");
            }
            catch (UserException e)
            {
                // Expected
            }
        }
        else
        {
            @Value BigDecimal expected = Utility.toBigDecimal(Utility.divideNumbers(total, DataTypeUtility.value(src.size())));
            @Value BigDecimal actual = Utility.toBigDecimal(runNumericSummaryFunction(u.toString(), src, u.toString(), new Mean()));
            assertThat("Expected " + expected + " actual" + actual, Utility.toBigDecimal(Utility.addSubtractNumbers(actual, expected, false)).abs(), Matchers.lessThanOrEqualTo(new BigDecimal("0.000001")));
        }
    }

    // Tests single numeric input, numeric output function
    @OnThread(Tag.Simulation)
    private @Value Number runNumericFunction(String expectedUnit, @Value Number src, String srcUnit, FunctionDefinition function) throws InternalException, UserException, Throwable
    {
        if (mgr == null)
            throw new RuntimeException();
        try
        {
            @Nullable Pair<ValueFunction, DataType> instance = TFunctionUtil.typeCheckFunction(function, ImmutableList.of(DataType.number(new NumberInfo(mgr.loadUse(srcUnit)))));
            assertNotNull(instance);
            // Won't happen, but for nullness checker:
            if (instance == null) throw new RuntimeException();
            assertTrue(DataTypeUtility.isNumber(instance.getSecond()));
            assertEquals(mgr.loadUse(expectedUnit), TFunctionUtil.getUnit(instance.getSecond()));
            @Value Object num = instance.getFirst().call(new @Value Object[] {src});
            return Utility.cast(num, Number.class);
        }
        catch (RuntimeException e)
        {
            if (e.getCause() != null)
                throw e.getCause();
            else
                throw e;
        }
    }

    // Tests single numeric input, numeric output function
    @OnThread(Tag.Simulation)
    private @Value Number runNumericSummaryFunction(String expectedUnit, List<@Value Number> src, String srcUnit, FunctionDefinition function) throws InternalException, UserException, Throwable
    {
        if (mgr == null)
            throw new RuntimeException();
        try
        {
            @Nullable Pair<ValueFunction, DataType> instance = TFunctionUtil.typeCheckFunction(function, ImmutableList.of(DataType.array(DataType.number(new NumberInfo(mgr.loadUse(srcUnit))))));
            assertNotNull(instance);
            // Won't happen, but for nullness checker:
            if (instance == null) throw new RuntimeException();
            assertTrue(DataTypeUtility.isNumber(instance.getSecond()));
            assertEquals(mgr.loadUse(expectedUnit), TFunctionUtil.getUnit(instance.getSecond()));
            @Value Object num = instance.getFirst().call(new @Value Object[] {DataTypeUtility.value(Utility.<@Value Number, @Value Object>mapList(src, s -> s))});
            return Utility.cast(num, Number.class);
        }
        catch (RuntimeException e)
        {
            if (e.getCause() != null)
                throw e.getCause();
            else
                throw e;
        }
    }
}
