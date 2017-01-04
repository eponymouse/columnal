package records.transformations.function;

import records.error.InternalException;
import records.error.UserException;

import java.util.List;

/**
 * Created by neil on 11/12/2016.
 */
public abstract class FunctionInstance
{
    public abstract Object getValue(int rowIndex, List<Object> params) throws UserException, InternalException;
}
