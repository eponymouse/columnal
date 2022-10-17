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

package test;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.hamcrest.CustomMatcher;
import org.hamcrest.Matcher;
import org.hamcrest.MatcherAssert;
import org.junit.Before;
import org.junit.Test;
import org.sosy_lab.common.rationals.Rational;
import test.functions.TFunctionUtil;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.NumberInfo;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.unit.SingleUnit;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.data.unit.UnitManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.function.core.AsUnit;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.Utility;
import xyz.columnal.transformations.expression.function.ValueFunction;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertThrows;

/**
 * Created by neil on 11/12/2016.
 */
@SuppressWarnings("initialization")
public class TestUnit
{
    private @NonNull UnitManager mgr;
    @Before
    public void init() throws UserException, InternalException
    {
        mgr = new UnitManager();
    }

    @Test
    public void unitToString() throws InternalException
    {
        SingleUnit m = mgr.getDeclared("m");
        SingleUnit g = mgr.getDeclared("g");
        SingleUnit l = mgr.getDeclared("l");
        SingleUnit s = mgr.getDeclared("s");
        SingleUnit d = mgr.getDeclared("USD");
        assertEquals("m", Unit._test_make(m, 1).toString());
        assertEquals("1/m", Unit._test_make(m, -1).toString());
        assertEquals("1/s^2", Unit._test_make(s, -2).toString());
        assertEquals("m/s", Unit._test_make(m, 1, s, -1).toString());
        assertEquals("m/s^2", Unit._test_make(m, 1, s, -2).toString());
        assertEquals("m/(g^3*s^2)", Unit._test_make(m, 1, s, -2, g, -3).toString());
        assertEquals("m/(g^3*l*s^2)", Unit._test_make(m, 1, s, -2, l, -1, g, -3).toString());
        assertEquals("(l*m)/(g^3*s^2)", Unit._test_make(m, 1, s, -2, l, 1, g, -3).toString());
        assertEquals("m/(USD*s^2)", Unit._test_make(m, 1, d, -1, s, -2).toString());
        assertEquals("1/(USD*s^2)", Unit._test_make(s, -2, d, -1).toString());
        assertEquals("1/(USD*g^3*s^2)", Unit._test_make(g, -3, s, -2, d, -1).toString());
    }
    
    @Test
    public void parse() throws InternalException, UserException
    {
        SingleUnit m = mgr.getDeclared("m");
        SingleUnit g = mgr.getDeclared("g");
        SingleUnit l = mgr.getDeclared("l");
        SingleUnit s = mgr.getDeclared("s");
        assertEquals(Unit._test_make(m, 1), mgr.loadUse("m"));
        assertEquals(Unit._test_make(m, 2), mgr.loadUse("m^2"));
        assertEquals(Unit._test_make(m, 2), mgr.loadUse("m*m"));
        assertEquals(Unit._test_make(m, 3), mgr.loadUse("m^3"));
        assertEquals(Unit._test_make(m, 3), mgr.loadUse("m*m*m"));
        assertEquals(Unit._test_make(m, 3), mgr.loadUse("m^2*m"));
        assertEquals(Unit._test_make(m, 3), mgr.loadUse("m*m^2"));
        assertEquals(Unit._test_make(), mgr.loadUse("m^0"));
        assertEquals(Unit._test_make(), mgr.loadUse("m/m"));

        assertEquals(Unit._test_make(m, -2), mgr.loadUse("m^-2"));
        assertEquals(Unit._test_make(m, -2), mgr.loadUse("1/m^2"));
        assertEquals(Unit._test_make(m, -2), mgr.loadUse("(1/m)*(m^-1)"));
        
        assertEquals(Unit._test_make(m, 1, s, -2), mgr.loadUse("m/s^2"));
        assertEquals(Unit._test_make(m, 1, s, -2), mgr.loadUse("(m/s^2)"));
        assertEquals(Unit._test_make(m, 1, s, -2), mgr.loadUse("m*s^-2"));
        assertEquals(Unit._test_make(m, 1, s, -2), mgr.loadUse("(m/s)/s"));
        assertEquals(Unit._test_make(m, 1, s, -2), mgr.loadUse("m/(s*s)"));
        assertEquals(Unit._test_make(m, 1, s, -2), mgr.loadUse("m/(s^2)"));
        assertEquals(Unit._test_make(m, 1, s, -2), mgr.loadUse("m*(s^-2)"));
        assertEquals(Unit._test_make(m, 2, s, -2), mgr.loadUse("m*(m/s^2)"));

        assertEquals(Unit._test_make(m, 1, s, -2, l, 1, g, -3), mgr.loadUse("(m*l)/(g^3*s^2)"));
        assertEquals(Unit._test_make(m, 1, s, -2, l, 1, g, -3), mgr.loadUse("((m*l)/(g^3*s)) / s"));
        assertEquals(Unit._test_make(m, 1, s, -2, l, 1, g, -3), mgr.loadUse("((m*l)/(g^3*s))/s"));
        assertEquals(Unit._test_make(m, 1, s, -2, l, -1, g, -3), mgr.loadUse("m/(g^3*l*s^2)"));
        assertEquals(Unit._test_make(m, 1, s, -2, l, -1, g, -3), mgr.loadUse("m/(g * l * g * s * g*s)"));
        assertEquals(Unit._test_make(m, 1, s, -2, l, -1, g, -3), mgr.loadUse("m/(g * l * g^2*s * s)"));
    }

    @Test
    public void testCanon() throws InternalException, UserException
    {
        SingleUnit m = mgr.getDeclared("m");
        SingleUnit g = mgr.getDeclared("g");
        SingleUnit l = mgr.getDeclared("l");
        SingleUnit s = mgr.getDeclared("s");
        assertEquals(new Pair<>(Rational.of("1"), Unit._test_make(m, 1)), mgr.canonicalise(mgr.loadUse("m")));
        assertEquals(new Pair<>(Rational.of("1/100"), Unit._test_make(m, 1)), mgr.canonicalise(mgr.loadUse("cm")));
        assertEquals(new Pair<>(Rational.of("1/10000"), Unit._test_make(m, 2)), mgr.canonicalise(mgr.loadUse("cm^2")));
        assertEquals(new Pair<>(Rational.of("1/1000"), Unit._test_make(m, 1)), mgr.canonicalise(mgr.loadUse("mm")));
        assertEquals(new Pair<>(Rational.of("1/1000000"), Unit._test_make(m, 2)), mgr.canonicalise(mgr.loadUse("mm^2")));
        assertEquals(new Pair<>(Rational.of("1/100"), Unit._test_make(m, 1)), mgr.canonicalise(mgr.loadUse("cm")));
        assertEquals(new Pair<>(Rational.of("254/10000"), Unit._test_make(m, 1)), mgr.canonicalise(mgr.loadUse("inch")));
        assertEquals(new Pair<>(Rational.of("254/36000000"), Unit._test_make(m, 1, s, -1)), mgr.canonicalise(mgr.loadUse("inch/hour")));
        assertEquals(new Pair<>(Rational.of("100"), Unit._test_make()), mgr.canonicalise(mgr.loadUse("m/cm")));
        assertEquals(new Pair<>(Rational.of("1/100"), Unit._test_make()), mgr.canonicalise(mgr.loadUse("cm/m")));
        assertEquals(new Pair<>(Rational.of("1"), Unit._test_make(m ,1)), mgr.canonicalise(mgr.loadUse("m^3/(m^2)")));
    }

    @Test
    public void parseFail()
    {
        // This is both parse failures and unknown unit failures
        assertThrows(UserException.class, () -> mgr.loadUse("###"));
        // Need space inbetween to be valid:
        assertThrows(UserException.class, () -> mgr.loadUse("$$"));
        assertThrows(UserException.class, () -> mgr.loadUse("ss"));
        assertThrows(UserException.class, () -> mgr.loadUse("m/s/"));
        assertThrows(UserException.class, () -> mgr.loadUse("m/s/s"));
        assertThrows(UserException.class, () -> mgr.loadUse("m l/(g^3 s) / s"));
        assertThrows(UserException.class, () -> mgr.loadUse("m^0.5"));
        assertThrows(UserException.class, () -> mgr.loadUse("m^-0.5"));
    }

    @Test
    @OnThread(Tag.Simulation)
    public void testAs() throws Throwable
    {
        test("3.2808399", "foot", "1", "m");
        test("3.2808399", "foot", "100", "cm");
        test("3.2808399", "foot", "1000", "mm");
        test("6.63655303", "mile", "420492", "inch");
        test("26.8224", "m/s", "60", "mile/hour");
        test("2682.24", "cm*s^-1", "60", "mile/hour");
        test("45645.2419", "mile/hour^2", "5.6681247", "m/(s*s)");
        test("817684.876315", "inch^3/minute", "223.32424", "l/s");
        test("817684.876315", "inch^3/minute", "223324.24", "ml/s");
        test("817684.876315", "(inch*inch*inch)/minute", "223.32424", "l*s^-1");

        //TODO add failure tests, like converting scalar to/from units, or unrelated units, or m/s to m/s^2 etc
    }
    @Test
    @OnThread(Tag.Simulation)
    public void testAsFail()
    {
        assertThrows(UserException.class, () -> test_("1", "m", "1", "s"));
        assertThrows(UserException.class, () -> test_("1", "mm", "1", "s"));
        assertThrows(UserException.class, () -> test_("1", "m", "1", "m/s"));
        assertThrows(UserException.class, () -> test_("1", "1", "1", "m/s"));
        assertThrows(UserException.class, () -> test_("1", "1", "1", "m"));
        assertThrows(UserException.class, () -> test_("1", "m", "1", "1"));
    }

    @OnThread(Tag.Simulation)
    private void test(String expected, String destUnit, String src, String srcUnit) throws Throwable
    {
        test_(expected, destUnit, src, srcUnit);
        test_(src, srcUnit, expected, destUnit);
    }

    @SuppressWarnings("nullness")
    @OnThread(Tag.Simulation)
    private void test_(String expected, String destUnit, String src, String srcUnit) throws InternalException, UserException, Throwable
    {
        try
        {
            @Nullable Pair<ValueFunction, DataType> instance = TFunctionUtil.typeCheckFunction(new AsUnit(),
                ImmutableList.of(
                    DummyManager.make().getTypeManager().unitGADTFor(mgr.loadUse(destUnit)),
                    DataType.number(new NumberInfo(mgr.loadUse(srcUnit)))
                )
            );
            assertNotNull(instance);
            Object num = instance.getFirst().call(new @Value Object[]{null, d(src)});
            MatcherAssert.assertThat(num, numberMatch(d(expected)));
        }
        catch (RuntimeException e)
        {
            if (e.getCause() != null)
                throw e.getCause();
            else
                throw e;
        }
    }

    @SuppressWarnings("valuetype")
    private Matcher<Object> numberMatch(BigDecimal n)
    {
        return new CustomMatcher<Object>(n.toPlainString()) {

            @Override
            public boolean matches(Object o)
            {
                int cmp = Utility.compareNumbers((Number)o, n);
                return cmp == 0 || Utility.compareNumbers(cmp < 0 ? Utility.addSubtractNumbers(n, (Number) o, false) : Utility.addSubtractNumbers((Number) o, n, false), d("0.0001")) < 0;
            }
        };
    }

    private @Value BigDecimal d(String s)
    {
        return DataTypeUtility.value(new BigDecimal(s));
    }
}
