package test;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.hamcrest.BaseMatcher;
import org.hamcrest.CustomMatcher;
import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Test;
import records.data.datatype.DataType;
import records.data.datatype.DataType.NumberInfo;
import records.data.unit.SingleUnit;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.function.AsType;
import records.transformations.function.FunctionInstance;
import utility.Pair;
import utility.Utility;

import java.math.BigDecimal;
import java.util.Collections;

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
        SingleUnit s = mgr.getDeclared("s");
        SingleUnit d = mgr.getDeclared("$");
        assertEquals("m", Unit._test_make(1, m, 1).toString());
        assertEquals("m^-1", Unit._test_make(1, m, -1).toString());
        assertEquals("3m", Unit._test_make(3, m, 1).toString());
        assertEquals("3m^-1", Unit._test_make(3, m, -1).toString());
        assertEquals("3s^-1", Unit._test_make(3, s, -1).toString());
        assertEquals("3m/s", Unit._test_make(3, m, 1, s, -1).toString());
        assertEquals("3m/s^2", Unit._test_make(3, m, 1, s, -2).toString());

        assertEquals("m/($ s^2)", Unit._test_make(3, m, 1, d, -1, s, -2).toString());
    }

    @Test
    public void parse() throws InternalException, UserException
    {
        SingleUnit m = mgr.getDeclared("m");
        SingleUnit s = mgr.getDeclared("s");
        assertEquals(Unit._test_make(1, m, 1), mgr.loadUse("m"));
        assertEquals(Unit._test_make(2, m, 1), mgr.loadUse("2m"));
        assertEquals(Unit._test_make(1, m, 2), mgr.loadUse("m^2"));
        assertEquals(Unit._test_make(1, m, 2), mgr.loadUse("m m"));
        assertEquals(Unit._test_make(1, m, 2), mgr.loadUse("m*m"));
        assertEquals(Unit._test_make(1, m, 3), mgr.loadUse("m^3"));
        assertEquals(Unit._test_make(1, m, 3), mgr.loadUse("m m*m"));
        assertEquals(Unit._test_make(1, m, 3), mgr.loadUse("m*m m"));
        assertEquals(Unit._test_make(1, m, 3), mgr.loadUse("m^2 m"));
        assertEquals(Unit._test_make(1, m, 3), mgr.loadUse("m m^2"));

        assertEquals(Unit._test_make(1000, m, 3), mgr.loadUse("1000 m^2 m"));
        assertEquals(Unit._test_make(1000, m, 3), mgr.loadUse("10^3 m^2 m"));
        assertEquals(Unit._test_make(1000, m, 3), mgr.loadUse("10^2 m^2 10 m"));

        assertEquals(Unit._test_make(1, m, 1, s, -2), mgr.loadUse("m/s^2"));
        assertEquals(Unit._test_make(1, m, 1, s, -2), mgr.loadUse("m s^-2"));
        assertEquals(Unit._test_make(1, m, 1, s, -2), mgr.loadUse("(m/s)/s"));
        assertEquals(Unit._test_make(1, m, 1, s, -2), mgr.loadUse("m/(s s)"));
        assertEquals(Unit._test_make(1, m, 1, s, -2), mgr.loadUse("m/(s^2)"));
        assertEquals(Unit._test_make(1, m, 1, s, -2), mgr.loadUse("m*(s^-2)"));
        assertEquals(Unit._test_make(1, m, 1, s, -2), mgr.loadUse("m*(1/s^2)"));

        //TODO keep adding parser test, including test that invalid units don't parse (e.g. m/s/s)
    }

    public void parseFail()
    {
        // This is both parse failures and unknown unit failures
        assertThrows(UserException.class, () -> mgr.loadUse("###"));
        // Need space inbetween to be valid:
        assertThrows(UserException.class, () -> mgr.loadUse("$$"));
        assertThrows(UserException.class, () -> mgr.loadUse("mm"));
        assertThrows(UserException.class, () -> mgr.loadUse("m/s/"));
        assertThrows(UserException.class, () -> mgr.loadUse("m/s/s"));
    }

    //TODO test canonicalise explicitly


    @Test
    public void testAs() throws UserException, InternalException
    {
        test("3.2808399", "foot", "1", "m");
        test("3.2808399", "foot", "100", "cm");
        test("3.2808399", "foot", "10", "10cm");
        test("6.63655303", "mile", "420492", "inch");
        test("26.8224", "m/s", "60", "mile/hour");
        test("26.8224", "100cm s^-1", "60", "mile/hour");
        test("45645.2419", "mile/hour^2", "5.6681247", "m/(s*s)");
        test("817684.876315", "inch^3/min", "223.32424", "l/s");
        test("817684.876315", "inch*inch*inch/min", "223.32424", "l s^-1");

        //TODO add failure tests, like converting scalar to/from units, or unrelated units, or m/s to m/s^2 etc
    }
    private void test(String expected, String destUnit, String src, String srcUnit) throws InternalException, UserException
    {
        test_(expected, destUnit, src, srcUnit);
        test_(src, srcUnit, expected, destUnit);
    }

    @SuppressWarnings("nullness")
    private void test_(String expected, String destUnit, String src, String srcUnit) throws InternalException, UserException
    {
        @Nullable Pair<FunctionInstance, DataType> instance = new AsType().typeCheck(Collections.singletonList(mgr.loadUse(destUnit)), Collections.singletonList(DataType.number(new NumberInfo(mgr.loadUse(srcUnit), 0))), s ->
        {
            throw new RuntimeException(s);
        }, mgr);
        assertNotNull(instance);
        Object num = instance.getFirst().getValue(0, Collections.singletonList(Collections.singletonList((Object)d(src)))).get(0);
        assertThat(num, numberMatch(d(expected)));

    }

    private Matcher<Object> numberMatch(BigDecimal n)
    {
        return new CustomMatcher<Object>(n.toPlainString()) {

            @Override
            public boolean matches(Object o)
            {

                int cmp = Utility.compareNumbers(o, n);
                return cmp == 0 || Utility.compareNumbers(cmp < 0 ? Utility.addSubtractNumbers(n, (Number) o, false) : Utility.addSubtractNumbers((Number) o, n, false), d("0.0001")) < 0;
            }
        };
    }

    private BigDecimal d(String s)
    {
        return new BigDecimal(s);
    }
}
