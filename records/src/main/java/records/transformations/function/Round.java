package records.transformations.function;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import records.error.InternalException;
import records.error.UserException;
import utility.Utility;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Created by neil on 13/12/2016.
 */
public class Round extends SingleNumericInOutFunction
{
    public Round()
    {
        super("round");
    }

    @Override
    protected FunctionInstance makeInstance()
    {
        return new FunctionInstance()
        {
            @Override
            public @Value Object getValue(int rowIndex, ImmutableList<@Value Object> params) throws UserException, InternalException
            {
                return Utility.value(Utility.<Number>withNumber(params.get(0), x -> x, x -> x, d -> d.setScale(0, RoundingMode.HALF_EVEN)));
            }
        };
    }
}
