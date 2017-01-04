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
    private final Map<String, Object> variables;

    public EvaluateState()
    {
        this(new HashMap<>());
    }

    private EvaluateState(HashMap<String, Object> variables)
    {
        this.variables = variables;
    }

    public EvaluateState add(String varName, Object value) throws InternalException
    {
        HashMap<String, Object> copy = new HashMap<>(variables);
        if (copy.containsKey(varName))
        {
            throw new InternalException("Duplicate variable name: " + varName);
        }
        copy.put(varName, value);
        return new EvaluateState(copy);
    }

    public Object get(String varName) throws InternalException
    {
        Object value = variables.get(varName);
        if (value == null)
            throw new InternalException("Trying to access undeclared variable: \"" + varName + "\"");
        return value;
    }
}
