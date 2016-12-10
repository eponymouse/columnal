package test;

import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sosy_lab.common.rationals.Rational;
import records.data.RecordSet;
import records.data.datatype.DataType;
import records.data.datatype.DataType.NumberInfo;
import records.data.datatype.DataType.TagType;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.Expression;
import records.transformations.expression.TypeState;
import test.gen.GenExpressionValue;
import test.gen.GenTypecheckFail;
import utility.Pair;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static test.TestUtil.distinctTypes;

/**
 * Created by neil on 09/12/2016.
 */
@RunWith(JUnitQuickcheck.class)
public class PropTypecheck
{
    @Test
    public void testTypeComparison() throws InternalException, UserException
    {
        for (DataType a : distinctTypes)
        {
            for (DataType b : distinctTypes)
            {
                assertEqualIfSame(a, b);
            }
        }
    }

    @SuppressWarnings("intern")
    private void assertEqualIfSame(DataType a, DataType b) throws InternalException, UserException
    {
        // Equivalent to assertEquals(a == b, a.equals(b)) but gives better errors
        if (a == b)
        {
            assertEquals(a, b);
            assertEquals(a, b.copy((i, prog) -> Collections.<@NonNull Object>emptyList()));
        }
        else
        {
            assertNotEquals(a, b);
            assertNotEquals(a, b.copy((i, prog) -> Collections.<@NonNull Object>emptyList()));
        }
    }

    @Property
    @SuppressWarnings("nullness")
    public void propTypeCheckFail(@From(GenTypecheckFail.class) GenTypecheckFail.TypecheckInfo src) throws InternalException, UserException
    {
        for (Expression expression : src.expressionFailures)
        {
            assertNull(expression.toString(), expression.check(src.recordSet, TestUtil.typeState(), (e, s) -> {}));
        }
    }

    @Property
    @SuppressWarnings("nullness")
    public void propTypeCheckSucceed(@From(GenExpressionValue.class) GenExpressionValue.ExpressionValue src) throws InternalException, UserException
    {
        StringBuilder b = new StringBuilder();
        @Nullable DataType checked = src.expression.check(src.recordSet, TestUtil.typeState(), (e, s) ->
        {
            b.append("Err in " + e + ": " + s);
        });
        assertEquals(src.expression.toString() + "\n" + b.toString(), src.type, checked);
    }

    //#error Have property which generates tables/expressions of given types, and check they don't typecheck

    //#error Have property which generates result and tables/expressions to get result, and check equality
}
