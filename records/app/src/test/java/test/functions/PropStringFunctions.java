package test.functions;

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
import records.transformations.function.*;
import test.TestUtil;
import test.gen.GenRandom;
import test.gen.GenValueList;
import test.gen.UnicodeStringGenerator;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;
import utility.Utility.ListEx;
import utility.Utility.ListExList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

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
    public void propTextLength(@From(UnicodeStringGenerator.class) String str) throws Throwable
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
    public void propTrim(@From(UnicodeStringGenerator.class) String orig, @From(GenRandom.class) Random r) throws Throwable
    {
        StringTrim function = new StringTrim();
        @Nullable Pair<FunctionInstance, DataType> checked = function.typeCheck(Collections.emptyList(), DataType.TEXT, s -> {}, mgr);
        if (checked == null)
        {
            fail("Type check failure");
        }
        else
        {
            assertEquals(DataType.TEXT, checked.getSecond());
            String SPACES = " \n\t\r\u00A0\u1680\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007\u2008\u2009\u200A";
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

            @Value String actual = (String)checked.getFirst().getValue(0, DataTypeUtility.value(withSpaces));
                assertEquals(orig, actual);

        }
    }


    @Property
    @OnThread(Tag.Simulation)
    public void propLeft(@From(UnicodeStringGenerator.class) String str) throws Throwable
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
    public void propRight(@From(UnicodeStringGenerator.class) String str) throws Throwable
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

    @Property
    @OnThread(Tag.Simulation)
    public void propMid(@From(UnicodeStringGenerator.class) String str) throws Throwable
    {
        StringMid function = new StringMid();
        @Nullable Pair<FunctionInstance, DataType> checked = function.typeCheck(Collections.emptyList(), DataType.tuple(DataType.TEXT, DataType.NUMBER, DataType.NUMBER), s -> {}, mgr);
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
                    @Value String actual = (String) checked.getFirst().getValue(0, DataTypeUtility.value(new Object[]{str, start + 1, length}));
                    assertTrue(str.contains(actual));
                    assertEquals("From #" + (start + 1) + " for " + length + " in " + strCodepoints.length, new String(strCodepoints, Math.min(start, strCodepoints.length), Math.min(length, Math.max(0, strCodepoints.length - start))), actual);
                }
            }

            @Nullable Pair<FunctionInstance, DataType> checkedFinal = checked;
            TestUtil.assertUserException(() -> checkedFinal.getFirst().getValue(0, DataTypeUtility.value(new Object[]{str, -1, 0})));
            TestUtil.assertUserException(() -> checkedFinal.getFirst().getValue(0, DataTypeUtility.value(new Object[]{str, 0, -1})));
            TestUtil.assertUserException(() -> checkedFinal.getFirst().getValue(0, DataTypeUtility.value(new Object[]{str, -1, -1})));
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
        FunctionDefinition replaceFunction = new StringReplaceAll();
        @Nullable Pair<FunctionInstance, DataType> checked = function.typeCheck(Collections.emptyList(), DataType.tuple(DataType.TEXT, DataType.TEXT), _s -> {}, mgr);
        @Nullable Pair<FunctionInstance, DataType> checkedIndex = functionIndex.typeCheck(Collections.emptyList(), DataType.tuple(DataType.TEXT, DataType.TEXT), _s -> {}, mgr);
        @Nullable Pair<FunctionInstance, DataType> checkedReplace = replaceFunction.typeCheck(Collections.emptyList(), DataType.tuple(DataType.TEXT, DataType.TEXT, DataType.TEXT), _s -> {}, mgr);

        if (checked == null || checkedIndex == null || checkedReplace == null)
        {
            fail("Type check failure");
        }
        else
        {
            assertEquals(DataType.BOOLEAN, checked.getSecond());
            @Value Boolean actualContained = (Boolean) checked.getFirst().getValue(0, DataTypeUtility.value(new Object[]{target, s.toString()}));
            assertEquals(targets.stream().anyMatch(b -> b), actualContained);

            assertEquals(DataType.array(DataType.NUMBER), checkedIndex.getSecond());
            ListEx actualIndexes = (ListEx) checkedIndex.getFirst().getValue(0, DataTypeUtility.value(new Object[]{target, s.toString()}));
            List<Integer> expectedIndexes = new ArrayList<>();
            int index = 0;
            for (Boolean match : targets)
            {
                if (match)
                    expectedIndexes.add(index);
                index += match ? target.codePointCount(0, target.length()) : distractor.codePointCount(0, distractor.length());
            }
            assertEquals(new ListExList(expectedIndexes), actualIndexes);


            assertEquals(DataType.TEXT, checkedReplace.getSecond());
            assertEquals(replaced.toString(), checkedReplace.getFirst().getValue(0, new Object[]{target, replacement, s.toString()}));
        }
    }

    @Property
    @OnThread(Tag.Simulation)
    public void propShow()
    {
        fail("TODO: test anything->string show function");
    }
}
