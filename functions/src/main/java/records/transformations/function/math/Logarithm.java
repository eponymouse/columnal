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
import records.transformations.function.ValueFunction2;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.SimulationFunction;
import utility.Utility;
import records.transformations.expression.function.ValueFunction;

import java.math.BigDecimal;
import java.math.MathContext;

public class Logarithm extends FunctionDefinition
{
    public Logarithm() throws InternalException
    {
        super("math:log");
    }

    @Override
    public @OnThread(Tag.Simulation) ValueFunction getInstance(TypeManager typeManager, SimulationFunction<String, Either<Unit, DataType>> paramTypes) throws InternalException, UserException
    {
        return new ValueFunction2<Number, Number>(Number.class, Number.class) {

            @Override
            public @OnThread(Tag.Simulation) @Value Object call2(@Value Number x, @Value Number base) throws InternalException, UserException
            {
                try
                {
                    @Value BigDecimal bd = Utility.toBigDecimal(x);
                    final BigDecimal r;
                    if (Utility.compareNumbers(base, 10) == 0)
                        r = BigDecimalMath.log10(bd, MathContext.DECIMAL128);
                    else if (Utility.compareNumbers(base, 2) == 0)
                        r = BigDecimalMath.log2(bd, MathContext.DECIMAL128);
                    else
                        r = BigDecimalMath.log(bd, MathContext.DECIMAL128).divide(BigDecimalMath.log(Utility.toBigDecimal(base), MathContext.DECIMAL128), MathContext.DECIMAL128);
                    return DataTypeUtility.value(r);
                }
                catch (ArithmeticException e)
                {
                    throw new UserException("Error while taking logarithm: " + e.getLocalizedMessage(), e);
                }
            }
        };
    }
}
