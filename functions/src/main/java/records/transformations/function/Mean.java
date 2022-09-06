package records.transformations.function;

import annotation.qual.Value;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.utility.Either;
import xyz.columnal.utility.SimulationFunction;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Utility.ListEx;
import records.transformations.expression.function.ValueFunction;

/**
 * Created by neil on 22/01/2017.
 */
public class Mean extends SingleNumericSummaryFunction
{
    public static final String NAME = "average";

    public Mean() throws InternalException
    {
        super("number:average");
    }

    @Override
    public ValueFunction getInstance(TypeManager typeManager, SimulationFunction<String, Either<Unit, DataType>> paramTypes) throws InternalException
    {
        return new ValueFunction()
        {
            @Override
            public @Value Object _call() throws UserException, InternalException
            {
                ListEx list = arg(0, ListEx.class);
                int size = list.size();
                if (size == 0)
                    throw new UserException("Cannot calculate average of empty list");
                @Value Number average = DataTypeUtility.value(0L);
                for (int i = 0; i < size; i++)
                {
                    // From http://stackoverflow.com/questions/1346824/is-there-any-way-to-find-arithmetic-mean-better-than-sum-n
                    average = Utility.addSubtractNumbers(average, Utility.divideNumbers(Utility.addSubtractNumbers(Utility.valueNumber(list.get(i)), average, false), DataTypeUtility.value(i+1)), true);
                }
                return average;
            }
        };
    }
}
