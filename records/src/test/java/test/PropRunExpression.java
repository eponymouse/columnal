package test;

import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.junit.runner.RunWith;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.EvaluateState;
import test.gen.ExpressionValue;
import test.gen.GenExpressionValueBackwards;
import test.gen.GenExpressionValueForwards;
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
    @Property(trials = 10000)
    @OnThread(Tag.Simulation)
    public void propRunExpression(@From(GenExpressionValueBackwards.class) @From(GenExpressionValueForwards.class) ExpressionValue src) throws InternalException, UserException
    {
        try
        {
            src.expression.check(src.recordSet, TestUtil.typeState(), (e, s) ->
            {
                throw new InternalException(s);
            });
            Object actualValue = src.expression.getValue(0, new EvaluateState());
            assertTrue("{{{" + src.expression.toString() + "}}} should have been " + src.value + " but was " + actualValue + " columns: " + src.recordSet.getColumnIds().stream().map(Object::toString).collect(Collectors.joining(", ")) + " " + src.recordSet.debugGetVals(0),
                Utility.compareValues(src.value, actualValue, new BigDecimal("0.000000001")) == 0);
        }
        catch (ArithmeticException | InternalException | UserException | ClassCastException e)
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
