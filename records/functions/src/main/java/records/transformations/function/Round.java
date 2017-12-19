package records.transformations.function;

import annotation.qual.Value;
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.error.UserException;
import utility.Utility;

import java.math.RoundingMode;

/**
 * Created by neil on 13/12/2016.
 */
public class Round extends SingleNumericInOutFunction
{
    public Round()
    {
        super("round", () -> new FunctionInstance()
        {
            @Override
            public @Value Object getValue(int rowIndex, @Value Object params) throws UserException, InternalException
            {
                return DataTypeUtility.value(Utility.<Number>withNumber(params, x -> x, d -> d.setScale(0, RoundingMode.HALF_EVEN)));
            }
        });
    }
}
