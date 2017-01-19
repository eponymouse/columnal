package records.transformations.function;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.ArrayExpression;
import records.transformations.expression.Expression;
import records.transformations.expression.Expression._test_TypeVary;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExConsumer;
import utility.Pair;
import utility.Utility;
import utility.Utility.ListEx;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Created by neil on 19/01/2017.
 */
public class Sum extends SingleNumericSummaryFunction
{
    public Sum()
    {
        super("sum");
    }

    @Override
    protected FunctionInstance makeInstance()
    {
        return new Instance();
    }

    private static class Instance extends FunctionInstance
    {
        @Override
        public @OnThread(Tag.Simulation) @Value Object getValue(int rowIndex, ImmutableList<@Value Object> params) throws UserException, InternalException
        {
            // If there are non-integers this will get widened.  And if not, it will stay Integer
            // which will be faster:
            Number total = Integer.valueOf(0);
            ListEx list = Utility.valueList(params.get(0));
            int size = list.size();
            for (int i = 0; i < size; i++)
            {
                total = Utility.addSubtractNumbers(total, Utility.valueNumber(list.get(i)), true);
            }
            return Utility.value(total);
        }
    }
}
