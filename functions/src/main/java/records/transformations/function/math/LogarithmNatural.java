package records.transformations.function.math;

import annotation.qual.Value;
import ch.obermuhlner.math.big.BigDecimalMath;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.function.FunctionDefinition;
import records.transformations.function.ValueFunction1;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.SimulationFunction;
import utility.Utility;
import records.transformations.expression.function.ValueFunction;

import java.math.BigDecimal;
import java.math.MathContext;

public class LogarithmNatural extends FunctionDefinition
{
    public LogarithmNatural() throws InternalException
    {
        super("math:log natural");
    }

    @Override
    public @OnThread(Tag.Simulation) ValueFunction getInstance(TypeManager typeManager, SimulationFunction<String, Either<Unit, DataType>> paramTypes) throws InternalException, UserException
    {
        return new ValueFunction1<Number>(Number.class) {

            @Override
            public @OnThread(Tag.Simulation) @Value Object call1(@Value Number x) throws InternalException, UserException
            {
                try
                {
                    @Value BigDecimal bd = Utility.toBigDecimal(x);
                    return DataTypeUtility.value(BigDecimalMath.log(bd, MathContext.DECIMAL128));
                }
                catch (ArithmeticException e)
                {
                    throw new UserException("Error while taking logarithm: " + e.getLocalizedMessage(), e);
                }
            }
        };
    }
}
