package records.transformations.function;

import annotation.qual.Value;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import utility.Either;
import utility.SimulationFunction;
import utility.Utility;
import records.data.ValueFunction;

import java.math.BigDecimal;

/**
 * Created by neil on 13/12/2016.
 */
public class Absolute extends SingleNumericInOutFunction
{
    public Absolute() throws InternalException
    {
        super("number:abs");
    }
    
    @Override
    public ValueFunction getInstance(TypeManager typeManager, SimulationFunction<String, Either<Unit, DataType>> paramTypes)
    {
        return new ValueFunction()
        {
            @Override
            public @Value Object call() throws UserException, InternalException
            {
                return DataTypeUtility.value(Utility.<Number>withNumber(arg(0), l -> {
                    if (l == Long.MIN_VALUE)
                        return BigDecimal.valueOf(l).negate();
                    else
                        return Math.abs(l);
                }, BigDecimal::abs));
            }
        };
    }

}
