package records.transformations.expression;

import annotation.qual.Value;
import records.error.InternalException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by neil on 29/11/2016.
 */
public class EvaluateState
{
    private final Map<String, @Value Object> variables;

    public EvaluateState()
    {
        this(new HashMap<>());
    }

    private EvaluateState(HashMap<String, @Value Object> variables)
    {
        this.variables = variables;
    }

    public EvaluateState add(String varName, @Value Object value) throws InternalException
    {
        HashMap<String, @Value Object> copy = new HashMap<>(variables);
        if (copy.containsKey(varName))
        {
            throw new InternalException("Duplicate variable name: " + varName);
        }
        copy.put(varName, value);
        return new EvaluateState(copy);
    }

    public @Value Object get(String varName) throws InternalException
    {
        Object value = variables.get(varName);
        if (value == null)
            throw new InternalException("Trying to access undeclared variable: \"" + varName + "\"");
        return value;
    }
}
