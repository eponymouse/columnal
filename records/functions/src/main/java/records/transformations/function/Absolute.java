package records.transformations.function;

import annotation.qual.Value;
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.error.UserException;
import utility.Utility;
import utility.ValueFunction;

import java.math.BigDecimal;

/**
 * Created by neil on 13/12/2016.
 */
public class Absolute extends SingleNumericInOutFunction
{
    public Absolute()
    {
        super("number/abs", "abs.mini", Absolute::makeInstance);
    }
    
    private static ValueFunction makeInstance()
    {
        return new ValueFunction()
        {
            @Override
            public @Value Object call(@Value Object param) throws UserException, InternalException
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
