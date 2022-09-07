package xyz.columnal.transformations.function.math;

import annotation.qual.Value;
import ch.obermuhlner.math.big.BigDecimalMath;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.function.FunctionDefinition;
import xyz.columnal.transformations.function.ValueFunction1;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Either;
import xyz.columnal.utility.SimulationFunction;
import xyz.columnal.utility.Utility;
import xyz.columnal.transformations.expression.function.ValueFunction;

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
