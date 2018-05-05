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

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Created by neil on 13/12/2016.
 */
public class RoundSF extends FunctionDefinition
{
    public RoundSF() throws InternalException
    {
        super("number:round significant");
    }

    @Override
    public ValueFunction getInstance(SimulationFunction<String, DataType> paramTypes) throws InternalException
    {
        return new ValueFunction()
        {
            @Override
            public @Value Object call(@Value Object param) throws UserException, InternalException
            {
                @Value Object[] args = Utility.castTuple(param, 2);
                // From https://stackoverflow.com/questions/7572309/any-neat-way-to-limit-significant-figures-with-bigdecimal
                BigDecimal bd = Utility.toBigDecimal(Utility.cast(args[0], Number.class));
                int newScale = DataTypeUtility.requireInteger(args[1])-bd.precision()+bd.scale();
                
                bd = bd.setScale(newScale, RoundingMode.HALF_UP);
                return DataTypeUtility.value(bd);
            }
        };
        
    }
}
