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
import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import xyz.columnal.data.TBasicUtil;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.function.*;
import test.DummyManager;
import test.gen.GenRandom;
import test.gen.type.GenTypeAndValueGen;
import test.gen.type.GenTypeAndValueGen.TypeAndValueGen;
import test.gen.UnicodeStringGenerator;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.transformations.expression.function.ValueFunction;

import java.util.Random;

import static org.junit.Assert.*;

@RunWith(JUnitQuickcheck.class)
public class PropStringFunctions
{
    @Property
    @OnThread(Tag.Simulation)
    public void propTextLength(@From(UnicodeStringGenerator.class) String str) throws Throwable
    {
        StringLength function = new StringLength();
        @Nullable Pair<ValueFunction, DataType> checked = TFunctionUtil.typeCheckFunction(function, ImmutableList.of(DataType.TEXT));
        if (checked == null)
        {
            fail("Type check failure");
        }
        else
        {
            assertEquals(DataType.NUMBER, checked.getSecond());
            @Value Number actual = (Number)checked.getFirst().call(new @Value Object[] {DataTypeUtility.value(str)});
            assertEquals(str.codePointCount(0, str.length()), actual.intValue());
            assertTrue(actual.doubleValue() == (double)actual.intValue());
        }
    }

    @Property
    @OnThread(Tag.Simulation)
    public void propTrim(@From(UnicodeStringGenerator.class) String orig, @From(GenRandom.class) Random r) throws Throwable
    {
        // We don't want any spaces besides the ones we add!
        while (!orig.isEmpty() && CharMatcher.whitespace().matches(orig.charAt(0)))
            orig = orig.substring(1);
        while (!orig.isEmpty() && CharMatcher.whitespace().matches(orig.charAt(orig.length() - 1)))
            orig = orig.substring(0, orig.length() - 1);


        StringTrim function = new StringTrim();
        @Nullable Pair<ValueFunction, DataType> checked = TFunctionUtil.typeCheckFunction(function, ImmutableList.of(DataType.TEXT));
        if (checked == null)
        {
            fail("Type check failure");
        }
        else
        {
            assertEquals(DataType.TEXT, checked.getSecond());
            String SPACES = " \n\t\r\u1680\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2008\u2009\u200A\u2028\u2029\u202f\u205f\u3000";
            String withSpaces = orig;
            int before = r.nextInt(4);
            int after = r.nextInt(4);
            for (int i = 0; i < before; i++)
            {
                withSpaces = "" + SPACES.charAt(r.nextInt(SPACES.length())) + withSpaces;
            }
            for (int i = 0; i < after; i++)
            {
                withSpaces = withSpaces + SPACES.charAt(r.nextInt(SPACES.length()));
            }

            @Value String actual = (String)checked.getFirst().call(new @Value Object[] {DataTypeUtility.value(withSpaces)});
                assertEquals(orig, actual);

        }
    }

    // Shortcut method
    private static @Value String v(String s)
    {
        return DataTypeUtility.value(s);
    }

    private static @Value Integer v(int n)
    {
        return DataTypeUtility.value(n);
    }

    /*
    @Property
    @OnThread(Tag.Simulation)
    public void propLeft(@From(UnicodeStringGenerator.class) String str) throws Throwable
    {
        StringLeft function = new StringLeft();
        @Nullable Pair<ValueFunction, DataType> checked = TestUtil.typeCheckFunction(function, Collections.emptyList(), DataType.tuple(DataType.TEXT, DataType.NUMBER));
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
                @Value String actual = (String)checked.getFirst().call(DataTypeUtility.value(new @Value Object[]{v(str), v(i)}));
                assertTrue(str.startsWith(actual));
                assertEquals(new String(strCodepoints, 0, Math.min(i, strCodepoints.length)), actual);
            }

            @Nullable Pair<ValueFunction, DataType> checkedFinal = checked;
            TestUtil.assertUserException(() -> checkedFinal.getFirst().call(DataTypeUtility.value(new @Value Object[]{v(str), v(-1)})));
        }
    }

    @Property
    @OnThread(Tag.Simulation)
    public void propRight(@From(UnicodeStringGenerator.class) String str) throws Throwable
    {
        StringRight function = new StringRight();
        @Nullable Pair<ValueFunction, DataType> checked = TestUtil.typeCheckFunction(function, Collections.emptyList(), DataType.tuple(DataType.TEXT, DataType.NUMBER));
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
                @Value String actual = (String)checked.getFirst().call(DataTypeUtility.value(new @Value Object[]{v(str), v(i)}));
                assertTrue(str.endsWith(actual));
                assertEquals(new String(strCodepoints, Math.max(0, strCodepoints.length - i), Math.min(i, strCodepoints.length)), actual);
            }

            @Nullable Pair<ValueFunction, DataType> checkedFinal = checked;
            TestUtil.assertUserException(() -> checkedFinal.getFirst().call(DataTypeUtility.value(new Object[]{v(str), v(-1)})));
        }
    }

    @Property
    @OnThread(Tag.Simulation)
    public void propMid(@From(UnicodeStringGenerator.class) String str) throws Throwable
    {
        StringMid function = new StringMid();
        @Nullable Pair<ValueFunction, DataType> checked = TestUtil.typeCheckFunction(function, Collections.emptyList(), DataType.tuple(DataType.TEXT, DataType.NUMBER, DataType.NUMBER));
        if (checked == null)
        {
            fail("Type check failure");
        }
        else
        {
            assertEquals(DataType.TEXT, checked.getSecond());
            int[] strCodepoints = str.codePoints().toArray();
            // Going beyond length should just return whole string, so go 5 beyond to check:
            for (int start = 0; start <= strCodepoints.length + 5; start++)
            {
                for (int length = 0; length <= strCodepoints.length + 5 - start; length++)
                {
                    // Start should be one-based, so we add one when making the call:
                    @Value String actual = (String) checked.getFirst().call(DataTypeUtility.value(new Object[]{v(str), v(start + 1), v(length)}));
                    assertTrue(str.contains(actual));
                    assertEquals("From #" + (start + 1) + " for " + length + " in " + strCodepoints.length, new String(strCodepoints, Math.min(start, strCodepoints.length), Math.min(length, Math.max(0, strCodepoints.length - start))), actual);
                }
            }

            @Nullable Pair<ValueFunction, DataType> checkedFinal = checked;
            TestUtil.assertUserException(() -> checkedFinal.getFirst().call(DataTypeUtility.value(new Object[]{v(str), v(-1), v(0)})));
            TestUtil.assertUserException(() -> checkedFinal.getFirst().call(DataTypeUtility.value(new Object[]{v(str), v(0), v(-1)})));
            TestUtil.assertUserException(() -> checkedFinal.getFirst().call(DataTypeUtility.value(new Object[]{v(str), v(-1), v(-1)})));
        }
    }

    @Property
    @OnThread(Tag.Simulation)
    public void propContains(@From(UnicodeStringGenerator.class) String target, @From(UnicodeStringGenerator.class) String distractor, @From(UnicodeStringGenerator.class) String replacement, @From(GenRandom.class) Random r) throws Throwable
    {
        if (distractor.contains(target))
            return;
        List<Boolean> targets = new ArrayList<>();
        StringBuilder s = new StringBuilder();
        StringBuilder replaced = new StringBuilder();
        for (int i = 0; i < r.nextInt(8); i++)
        {
            boolean t = r.nextBoolean();
            targets.add(t);
            s.append(t ? target : distractor);
            replaced.append(t ? replacement : distractor);
        }
        StringWithin function = new StringWithin();
        StringWithinIndex functionIndex = new StringWithinIndex();
        FunctionDefinition replaceFunction = new StringReplace();
        @Nullable Pair<ValueFunction, DataType> checked = TestUtil.typeCheckFunction(function, Collections.emptyList(), DataType.tuple(DataType.TEXT, DataType.TEXT));
        @Nullable Pair<ValueFunction, DataType> checkedIndex = TestUtil.typeCheckFunction(functionIndex, Collections.emptyList(), DataType.tuple(DataType.TEXT, DataType.TEXT));
        @Nullable Pair<ValueFunction, DataType> checkedReplace = TestUtil.typeCheckFunction(replaceFunction, Collections.emptyList(), DataType.tuple(DataType.TEXT, DataType.TEXT, DataType.TEXT));

        if (checked == null || checkedIndex == null || checkedReplace == null)
        {
            fail("Type check failure");
        }
        else
        {
            assertEquals(DataType.BOOLEAN, checked.getSecond());
            @Value Boolean actualContained = (Boolean) checked.getFirst().call(DataTypeUtility.value(new @Value Object[]{v(target), v(s.toString())}));
            assertEquals(targets.stream().anyMatch(b -> b), actualContained);

            assertEquals(DataType.array(DataType.NUMBER), checkedIndex.getSecond());
            ListEx actualIndexes = (ListEx) checkedIndex.getFirst().call(DataTypeUtility.value(new @Value Object[]{v(target), v(s.toString())}));
            List<@Value Integer> expectedIndexes = new ArrayList<>();
            int index = 0;
            for (Boolean match : targets)
            {
                if (match)
                    expectedIndexes.add(v(index));
                index += match ? target.codePointCount(0, target.length()) : distractor.codePointCount(0, distractor.length());
            }
            assertEquals(new ListExList(expectedIndexes), actualIndexes);


            assertEquals(DataType.TEXT, checkedReplace.getSecond());
            assertEquals(replaced.toString(), checkedReplace.getFirst().call(DataTypeUtility.value(new @Value Object[]{v(target), v(replacement), v(s.toString())})));
        }
    }
    */

    @Property(trials = 200)
    @OnThread(Tag.Simulation)
    public void propShow(@From(GenTypeAndValueGen.class) TypeAndValueGen typeAndValueGen) throws UserException, InternalException
    {
        FunctionDefinition toString = new ToString();
        @SuppressWarnings("nullness") // Will throw if null
        @NonNull FunctionDefinition fromString = FunctionList.lookup(DummyManager.make().getUnitManager(), "from text");
        @Nullable Pair<ValueFunction, DataType> checkedToString = TFunctionUtil.typeCheckFunction(toString, ImmutableList.of(typeAndValueGen.getType()), typeAndValueGen.getTypeManager());
        @Nullable Pair<ValueFunction, DataType> checkedFromString = TFunctionUtil.typeCheckFunction(fromString, typeAndValueGen.getType(), ImmutableList.of(DataType.TEXT), typeAndValueGen.getTypeManager());
        if (checkedToString == null || checkedFromString == null)
        {
            fail("Type check failure");
        }
        else
        {
            assertEquals(DataType.TEXT, checkedToString.getSecond());
            assertEquals(typeAndValueGen.getType(), checkedFromString.getSecond());

            for (int i = 0; i < 100; i++)
            {
                @Value Object value = typeAndValueGen.makeValue();
                @Value Object asString = checkedToString.getFirst().call(new @Value Object[] {value});
                @Value Object roundTripped = checkedFromString.getFirst().call(new @Value Object[] {asString});
                TBasicUtil.assertValueEqual(asString.toString(), value, roundTripped);
            }
        }
    }
    
    @Test
    @OnThread(Tag.Simulation)
    public void testEscape() throws InternalException, UserException
    {
        @SuppressWarnings("nullness") // Will throw if null
        @NonNull FunctionDefinition toString = FunctionList.lookup(DummyManager.make().getUnitManager(), "to text");

        @Nullable Pair<ValueFunction, DataType> checkedToString = TFunctionUtil.typeCheckFunction(toString, DataType.TEXT, ImmutableList.of(DataType.TEXT), null);
        assertNotNull(checkedToString);
        if (checkedToString == null)
            return;
        ValueFunction f = checkedToString.getFirst();
        
        assertEquals("\"\"", f.call(new @Value Object[] {DataTypeUtility.value("")}));
        assertEquals("\"hi\"", f.call(new @Value Object[] {DataTypeUtility.value("hi")}));
        assertEquals("\"^q\"", f.call(new @Value Object[] {DataTypeUtility.value("\"")}));
        assertEquals("\"^c^q\"", f.call(new @Value Object[] {DataTypeUtility.value("^\"")}));
        assertEquals("\"^cc^cq\"", f.call(new @Value Object[] {DataTypeUtility.value("^c^q")}));
    }
}
