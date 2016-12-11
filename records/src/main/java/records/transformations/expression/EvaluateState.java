package records.transformations.expression;

import records.error.InternalException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    public List<Object> get(String varName) throws InternalException
    {
        List<Object> value = variables.get(varName);
        if (value == null)
            throw new InternalException("Trying to access undeclared variable: \"" + varName + "\"");
        return value;
    }
}
