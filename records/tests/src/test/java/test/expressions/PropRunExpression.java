package test.expressions;

import annotation.qual.Value;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.runner.RunWith;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.ErrorAndTypeRecorderStorer;
import records.transformations.expression.EvaluateState;
import records.transformations.expression.Expression.CheckedExp;
import records.transformations.expression.Expression.LocationInfo;
import test.DummyManager;
import test.TestUtil;
import test.gen.ExpressionValue;
import test.gen.GenExpressionValueBackwards;
import test.gen.GenExpressionValueForwards;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;
import utility.Utility.EpsilonType;

import java.math.BigDecimal;
import java.util.List;
import java.util.OptionalInt;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * Created by neil on 10/12/2016.
 */
@RunWith(JUnitQuickcheck.class)
public class PropRunExpression
{
    @Property(trials = 2000)
    @OnThread(Tag.Simulation)
    public void propRunExpression(
            @From(GenExpressionValueBackwards.class) @From(GenExpressionValueForwards.class) ExpressionValue src) throws InternalException, UserException
    {
        try
        {
            DummyManager dummyManager = TestUtil.managerWithTestTypes().getFirst();
            ErrorAndTypeRecorderStorer errors = new ErrorAndTypeRecorderStorer();
            @Nullable CheckedExp checked = src.expression.check(src, TestUtil.createTypeState(dummyManager.getTypeManager()), LocationInfo.UNIT_DEFAULT, errors);
            assertEquals("Checked iff error", checked == null, errors.getAllErrors().count() != 0);
            errors.withFirst(s -> {throw new InternalException(s.toPlain());});
            for (int row = 0; row < src.value.size(); row++)
            {
                @Value Object actualValue = src.expression.calculateValue(new EvaluateState(dummyManager.getTypeManager(), OptionalInt.of(row), errors)).value;
                assertTrue("{{{" + src.expression.toString() + "}}} should have been " + TestUtil.toString(src.value.get(row)) + " but was " + TestUtil.toString(actualValue) + " columns: " + src.recordSet.getColumnIds().stream().map(Object::toString).collect(Collectors.joining(", ")) + " " + src.recordSet.debugGetVals(row),
                    Utility.compareValues(src.value.get(row), actualValue, new Pair<>(EpsilonType.RELATIVE, new BigDecimal("0.000000001"))) == 0);
            }
        }
        catch (ArithmeticException | InternalException | UserException | ClassCastException e)
        {
            System.err.println("Expression: {{{" + src.expression.toString() + "}}} " + src.recordSet.debugGetVals());
            //throw e;
            e.printStackTrace();
            // Must fail, not throw, if we want to shrink:
            fail(e.getLocalizedMessage());
        }
    }

    private String toString(List<Object> value)
    {
        return "[" + value.stream().map(Object::toString).collect(Collectors.joining(", ")) + "]";
    }
}
