package records.transformations.function.number;

import annotation.qual.Value;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import records.transformations.function.FunctionDefinition;
import xyz.columnal.utility.Either;
import xyz.columnal.utility.SimulationFunction;
import xyz.columnal.utility.Utility;
import records.transformations.expression.function.ValueFunction;

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
    public ValueFunction getInstance(TypeManager typeManager, SimulationFunction<String, Either<Unit, DataType>> paramTypes) throws InternalException
    {
        return new ValueFunction()
        {
            @Override
            public @Value Object _call() throws UserException, InternalException
            {
                // From https://stackoverflow.com/questions/7572309/any-neat-way-to-limit-significant-figures-with-bigdecimal
                BigDecimal bd = Utility.toBigDecimal(arg(0, Number.class));
                int newScale = intArg(1)-bd.precision()+bd.scale();
                
                bd = bd.setScale(newScale, RoundingMode.HALF_UP);
                return DataTypeUtility.value(bd);
            }
        };
        
    }
}
