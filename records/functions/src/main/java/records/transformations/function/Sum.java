package records.transformations.function;

import annotation.qual.Value;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import utility.Either;
import utility.SimulationFunction;
import utility.Utility;
import utility.Utility.ListEx;
import utility.ValueFunction;

/**
 * Created by neil on 19/01/2017.
 */
public class Sum extends SingleNumericSummaryFunction
{
    public static final String NAME = "sum";

    public Sum() throws InternalException
    {
        super("number:sum");
    }

    @Override
    public ValueFunction getInstance(TypeManager typeManager, SimulationFunction<String, Either<Unit, DataType>> paramTypes) throws InternalException
    {
        return new Instance();
    }

    private static class Instance extends ValueFunction
    {
        @Override
        public @Value Object call(@Value Object param) throws UserException, InternalException
        {
            // If there are non-integers this will get widened.  And if not, it will stay Integer
            // which will be faster:
            @Value Number total = DataTypeUtility.value(Integer.valueOf(0));
            ListEx list = Utility.valueList(param);
            int size = list.size();
            for (int i = 0; i < size; i++)
            {
                total = Utility.addSubtractNumbers(total, Utility.valueNumber(list.get(i)), true);
            }
            return total;
        }
    }
}
