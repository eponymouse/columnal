package records.transformations.function;

import records.error.InternalException;
import records.error.UserException;
import utility.Utility;

import java.util.Collections;
import java.util.List;

/**
 * Helper class for when your params and return type have no tags
 * i.e. are all of length 1, and you don't want to faff around with
 * get(0) and Collections.singletonList
 *
 * TODO: This no longer applies. We should delete this class.
 */
public abstract class TaglessFunctionInstance extends FunctionInstance
{
    @Override
    public final Object getValue(int rowIndex, List<Object> params) throws UserException, InternalException
    {
        return getSimpleValue(rowIndex, params);
    }

    public abstract Object getSimpleValue(int rowIndex, List<Object> simpleParams) throws UserException, InternalException;
}
