package records.transformations.function.number;

import annotation.qual.Value;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.function.FunctionDefinition;
import utility.Either;
import utility.SimulationFunction;
import utility.Utility;
import utility.ValueFunction;

import java.math.RoundingMode;

/**
 * Created by neil on 13/12/2016.
 */
public class RoundDP extends FunctionDefinition
{
    public RoundDP() throws InternalException
    {
        super("number:round decimal");
    }

    @Override
    public ValueFunction getInstance(TypeManager typeManager, SimulationFunction<String, Either<Unit, DataType>> paramTypes) throws InternalException
    {
        return new ValueFunction()
        {
            @Override
            public @Value Object call(@Value Object param) throws UserException, InternalException
            {
                @Value Object[] args = Utility.castTuple(param, 2);
                return DataTypeUtility.value(Utility.<Number>withNumber(args[0], x -> x, d -> d.setScale(DataTypeUtility.requireInteger(args[1]), RoundingMode.HALF_UP)));
            }
        };
        
    }
}
