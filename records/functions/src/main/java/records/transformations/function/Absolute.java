package records.transformations.function;

import annotation.qual.Value;
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.error.UserException;
import utility.Utility;

import java.math.BigDecimal;

/**
 * Created by neil on 13/12/2016.
 */
public class Absolute extends SingleNumericInOutFunction
{
    public Absolute()
    {
        super("abs", Absolute::makeInstance);
    }

    private static FunctionInstance makeInstance()
    {
        return new FunctionInstance()
        {
            @Override
            public @Value Object getValue(int rowIndex, @Value Object param) throws UserException, InternalException
            {
                return DataTypeUtility.value(Utility.<Number>withNumber(param, l -> {
                    if (l == Long.MIN_VALUE)
                        return BigDecimal.valueOf(l).negate();
                    else
                        return Math.abs(l);
                }, BigDecimal::abs));
            }
        };
    }

}
