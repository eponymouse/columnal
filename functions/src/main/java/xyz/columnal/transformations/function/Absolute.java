package xyz.columnal.transformations.function;

import annotation.qual.Value;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.utility.Either;
import xyz.columnal.utility.SimulationFunction;
import xyz.columnal.utility.Utility;
import xyz.columnal.transformations.expression.function.ValueFunction;

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
            public @Value Object _call() throws UserException, InternalException
            {
                return Utility.<@Value Number>withNumber(arg(0), l -> {
                    if (l == Long.MIN_VALUE)
                        return DataTypeUtility.value(BigDecimal.valueOf(l).negate());
                    else
                        return DataTypeUtility.value(Math.abs(l));
                }, d -> DataTypeUtility.value(d.abs()));
            }
        };
    }

}
