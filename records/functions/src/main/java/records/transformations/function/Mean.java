package records.transformations.function;

import annotation.qual.Value;
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.error.UserException;
import utility.Utility;
import utility.Utility.ListEx;
import utility.ValueFunction;

/**
 * Created by neil on 22/01/2017.
 */
public class Mean extends SingleNumericSummaryFunction
{
    public static final String NAME = "average";

    public Mean()
    {
        super(NAME, "average.mini", () -> new ValueFunction()
        {
            @Override
            public Object call(@Value Object param) throws UserException, InternalException
            {
                ListEx list = Utility.valueList(param);
                int size = list.size();
                if (size == 0)
                    throw new UserException("Cannot calculate average of empty list");
                Number average = 0L;
                for (int i = 0; i < size; i++)
                {
                    // From http://stackoverflow.com/questions/1346824/is-there-any-way-to-find-arithmetic-mean-better-than-sum-n
                    average = Utility.addSubtractNumbers(average, Utility.divideNumbers(Utility.addSubtractNumbers(Utility.valueNumber(list.get(i)), average, false), i+1), true);
                }
                return DataTypeUtility.value(average);
            }
        });
    }
    
    public static FunctionGroup group()
    {
        return new FunctionGroup("average.short", new Mean());
    }
}
