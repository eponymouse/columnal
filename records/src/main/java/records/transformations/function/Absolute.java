package records.transformations.function;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.Expression;
import records.transformations.expression.Expression._test_TypeVary;
import utility.ExConsumer;
import utility.Pair;
import utility.Utility;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Created by neil on 13/12/2016.
 */
public class Absolute extends SingleNumericInOutFunction
{
    public Absolute()
    {
        super("abs");
    }

    @Override
    protected FunctionInstance makeInstance()
    {
        return new TaglessFunctionInstance()
        {
            @Override
            public Object getSimpleValue(int rowIndex, List<Object> params) throws UserException, InternalException
            {
                return Utility.<Number>withNumber(params.get(0), l -> {
                    if (l == Long.MIN_VALUE)
                        return BigInteger.valueOf(l).negate();
                    else
                        return Math.abs(l);
                }, BigInteger::abs, BigDecimal::abs);
            }
        };
    }

}
