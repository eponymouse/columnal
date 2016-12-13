package records.transformations.function;

import java.util.List;

/**
 * Created by neil on 11/12/2016.
 */
public abstract class FunctionInstance
{
    public abstract List<Object> getValue(int rowIndex, List<List<Object>> params);
}
