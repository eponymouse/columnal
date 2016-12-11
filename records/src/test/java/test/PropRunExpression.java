package test;

import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.junit.runner.RunWith;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.EvaluateState;
import test.gen.GenExpressionValue;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertTrue;

/**
 * Created by neil on 10/12/2016.
 */
@RunWith(JUnitQuickcheck.class)
public class PropRunExpression
{
    @Property(trials = 1000)
    @OnThread(Tag.Simulation)
    public void propRunExpression(@From(GenExpressionValue.class) GenExpressionValue.ExpressionValue src) throws InternalException, UserException
    {
        try
        {
            src.expression.check(src.recordSet, TestUtil.typeState(), (e, s) ->
            {
                throw new InternalException(s);
            });
            List<Object> actualValue = src.expression.getValue(0, new EvaluateState());
            assertTrue("{{{" + src.expression.toString() + "}}} should have been " + toString(src.value) + " but was " + toString(actualValue) + " columns: " + src.recordSet.debugGetVals(0),
                Utility.compareLists(src.value, actualValue, new BigDecimal("0.000000001")) == 0);
        }
        catch (ArithmeticException | InternalException | UserException e)
        {
            System.err.println(src.expression.toString() + " " + src.recordSet.debugGetVals());
            throw e;
        }
    }

    private String toString(List<Object> value)
    {
        return "[" + value.stream().map(Object::toString).collect(Collectors.joining(", ")) + "]";
    }
}
