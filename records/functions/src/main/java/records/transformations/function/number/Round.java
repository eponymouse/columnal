package records.transformations.function.number;

import annotation.qual.Value;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.function.FunctionDefinition;
import utility.SimulationFunction;
import utility.Utility;
import utility.ValueFunction;

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
    public ValueFunction getInstance(SimulationFunction<String, DataType> paramTypes) throws InternalException
    {
        return new ValueFunction()
        {
            @Override
            public @Value Object call(@Value Object params) throws UserException, InternalException
            {
                return DataTypeUtility.value(Utility.<Number>withNumber(params, x -> x, d -> d.setScale(0, RoundingMode.HALF_UP)));
            }
        };
    }
}
