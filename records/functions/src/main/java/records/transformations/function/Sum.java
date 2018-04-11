package records.transformations.function;

import annotation.qual.Value;
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.error.UserException;
import utility.Utility;
import utility.Utility.ListEx;
import utility.ValueFunction;

/**
 * Created by neil on 19/01/2017.
 */
public class Sum extends SingleNumericSummaryFunction
{
    public static final String NAME = "sum";

    public Sum()
    {
        super(NAME, "sum.mini", Instance::new);
    }
    
    public static FunctionGroup group()
    {
        return new FunctionGroup("sum.short", new Sum());
    }

    private static class Instance extends ValueFunction
    {
        @Override
        public @Value Object call(@Value Object param) throws UserException, InternalException
        {
            // If there are non-integers this will get widened.  And if not, it will stay Integer
            // which will be faster:
            Number total = Integer.valueOf(0);
            ListEx list = Utility.valueList(param);
            int size = list.size();
            for (int i = 0; i < size; i++)
            {
                total = Utility.addSubtractNumbers(total, Utility.valueNumber(list.get(i)), true);
            }
            return DataTypeUtility.value(total);
        }
    }
}
