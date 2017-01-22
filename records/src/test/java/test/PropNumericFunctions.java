package test;

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
import records.data.datatype.DataType;
import records.data.datatype.DataType.NumberInfo;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.function.Absolute;
import records.transformations.function.FunctionDefinition;
import records.transformations.function.FunctionInstance;
import records.transformations.function.Round;
import test.gen.GenNumber;
import test.gen.GenUnit;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;

import java.math.BigDecimal;
import java.util.Collections;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertThrows;

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
    public void propAbs(@From(GenNumber.class) Number src, @From(GenUnit.class) Unit u) throws Throwable
    {
        BigDecimal absed = Utility.toBigDecimal(runNumericFunction(u.toString(), src, u.toString(), new Absolute()));
        // Change *after* call; important so that we test diff classes above, but need BigDecimal for comparisons:
        src = Utility.toBigDecimal(src);
        assertThat(absed, Matchers.greaterThanOrEqualTo(BigDecimal.ZERO));
        assertThat(absed, Matchers.anyOf(Matchers.equalTo(src), Matchers.equalTo(Utility.addSubtractNumbers(0, src, false))));
    }

    @Property
    @OnThread(Tag.Simulation)
    public void propRound(@From(GenNumber.class) Number src, @From(GenUnit.class) Unit u) throws Throwable
    {
        BigDecimal rounded = Utility.toBigDecimal(runNumericFunction(u.toString(), src, u.toString(), new Round()));
        BigDecimal orig = Utility.toBigDecimal(src);
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

    // Tests single numeric input, numeric output function
    @SuppressWarnings("nullness")
    @OnThread(Tag.Simulation)
    private Number runNumericFunction(String expectedUnit, Number src, String srcUnit, FunctionDefinition function) throws InternalException, UserException, Throwable
    {
        try
        {
            @Nullable Pair<FunctionInstance, DataType> instance = function.typeCheck(Collections.emptyList(), DataType.number(new NumberInfo(mgr.loadUse(srcUnit), 0)), s ->
            {
                throw new RuntimeException(new UserException(s));
            }, mgr);
            assertNotNull(instance);
            assertTrue(instance.getSecond().isNumber());
            assertEquals(mgr.loadUse(expectedUnit), instance.getSecond().getNumberInfo().getUnit());
            Object num = instance.getFirst().getValue(0, Utility.value(src));
            return (Number)num;
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
