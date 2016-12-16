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
 */
public abstract class TaglessFunctionInstance extends FunctionInstance
{
    @Override
    public final List<Object> getValue(int rowIndex, List<List<Object>> params) throws UserException, InternalException
    {
        return Collections.singletonList(getSimpleValue(rowIndex, Utility.<List<Object>, Object>mapList(params, p -> p.get(0))));
    }

    public abstract Object getSimpleValue(int rowIndex, List<Object> simpleParams) throws UserException, InternalException;
}
