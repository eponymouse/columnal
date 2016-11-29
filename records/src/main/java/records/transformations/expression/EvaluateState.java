package records.transformations.expression;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Created by neil on 29/11/2016.
 */
public class EvaluateState
{
    private final Map<String, List<Object>> variables;

    public EvaluateState()
    {
        this(new HashMap<>());
    }

    private EvaluateState(HashMap<String, List<Object>> variables)
    {
        this.variables = variables;
    }

    public EvaluateState add(String varName, List<Object> value) throws InternalException
    {
        HashMap<String, List<Object>> copy = new HashMap<>(variables);
        if (copy.containsKey(varName))
        {
            throw new InternalException("Duplicate variable name: " + varName);
        }
        copy.put(varName, value);
        return new EvaluateState(copy);
    }
}
