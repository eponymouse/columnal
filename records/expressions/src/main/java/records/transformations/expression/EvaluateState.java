package records.transformations.expression;

import annotation.qual.Value;
import com.google.common.collect.ImmutableMap;
import records.data.datatype.TypeManager;
import records.error.InternalException;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by neil on 29/11/2016.
 */
public class EvaluateState
{
    private final TypeManager typeManager;
    private final ImmutableMap<String, @Value Object> variables;

    public EvaluateState(TypeManager typeManager)
    {
        this(ImmutableMap.of(), typeManager);
    }

    private EvaluateState(ImmutableMap<String, @Value Object> variables, TypeManager typeManager)
    {
        this.variables = variables;
        this.typeManager = typeManager;
    }

    public EvaluateState add(String varName, @Value Object value) throws InternalException
    {
        ImmutableMap.Builder<String, @Value Object> copy = ImmutableMap.builder();
        if (!varName.startsWith("?") && variables.containsKey(varName))
        {
            throw new InternalException("Duplicate variable name: " + varName);
        }
        copy.putAll(variables);
        copy.put(varName, value);
        return new EvaluateState(copy.build(), typeManager);
    }

    public @Value Object get(String varName) throws InternalException
    {
        @Value Object value = variables.get(varName);
        if (value == null)
            throw new InternalException("Trying to access undeclared variable: \"" + varName + "\"");
        return value;
    }

    public TypeManager getTypeManager()
    {
        return typeManager;
    }
}
