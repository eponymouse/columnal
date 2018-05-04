package records.transformations.expression;

import annotation.qual.Value;
import com.google.common.collect.ImmutableMap;
import records.data.datatype.TypeManager;
import records.error.InternalException;
import records.error.UserException;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;

/**
 * Created by neil on 29/11/2016.
 */
public class EvaluateState
{
    private final TypeManager typeManager;
    private final ImmutableMap<String, @Value Object> variables;
    private final OptionalInt rowIndex;

    public EvaluateState(TypeManager typeManager, OptionalInt rowIndex)
    {
        this(ImmutableMap.of(), typeManager, rowIndex);
    }

    private EvaluateState(ImmutableMap<String, @Value Object> variables, TypeManager typeManager, OptionalInt rowIndex)
    {
        this.variables = variables;
        this.typeManager = typeManager;
        this.rowIndex = rowIndex;
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
        return new EvaluateState(copy.build(), typeManager, rowIndex);
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

    public int getRowIndex() throws UserException
    {
        return rowIndex.orElseThrow(() -> new UserException("No row index available."));
    }
}
