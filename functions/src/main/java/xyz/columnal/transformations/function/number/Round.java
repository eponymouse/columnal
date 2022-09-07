package xyz.columnal.transformations.function.number;

import annotation.qual.Value;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.function.FunctionDefinition;
import xyz.columnal.utility.Either;
import xyz.columnal.utility.SimulationFunction;
import xyz.columnal.utility.Utility;
import xyz.columnal.transformations.expression.function.ValueFunction;

import java.math.RoundingMode;

/**
 * Created by neil on 13/12/2016.
 */
public class Round extends FunctionDefinition
{
    public Round() throws InternalException
    {
        super("number:round");
    }

    @Override
    public ValueFunction getInstance(TypeManager typeManager, SimulationFunction<String, Either<Unit, DataType>> paramTypes) throws InternalException
    {
        return new ValueFunction()
        {
            @Override
            public @Value Object _call() throws UserException, InternalException
            {
                return Utility.<@Value Number>withNumber(arg(0, Number.class), (@Value Long x) -> x, d -> DataTypeUtility.value(d.setScale(0, RoundingMode.HALF_UP)));
            }
        };
    }
}
