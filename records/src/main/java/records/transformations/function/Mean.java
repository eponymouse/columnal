package records.transformations.function;

import annotation.qual.Value;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;
import utility.Utility.ListEx;

/**
 * Created by neil on 22/01/2017.
 */
public class Mean extends SingleNumericSummaryFunction
{
    public Mean()
    {
        super("average");
    }

    @Override
    protected FunctionInstance makeInstance()
    {
        return new FunctionInstance()
        {
            @Override
            public @OnThread(Tag.Simulation) @Value Object getValue(int rowIndex, @Value Object param) throws UserException, InternalException
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
                return Utility.value(average);
            }
        };
    }
}
