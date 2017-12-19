package records.transformations.function;

import annotation.qual.Value;
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;
import utility.Utility.ListEx;

/**
 * Created by neil on 19/01/2017.
 */
public class Sum extends SingleNumericSummaryFunction
{
    public Sum()
    {
        super("sum", Instance::new);
    }

    private static class Instance extends FunctionInstance
    {
        @Override
        public @OnThread(Tag.Simulation) @Value Object getValue(int rowIndex, @Value Object param) throws UserException, InternalException
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
