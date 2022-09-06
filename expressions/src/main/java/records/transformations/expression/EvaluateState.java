package records.transformations.expression;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.Value;
import annotation.units.TableDataRowIndex;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import xyz.columnal.log.Log;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.DataType.FlatDataTypeVisitor;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.TypeManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import records.transformations.expression.explanation.Explanation.ExecutionType;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Utility;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.stream.Stream;

/**
 * Created by neil on 29/11/2016.
 */
public final class EvaluateState
{
    private final TypeManager typeManager;
    private final ImmutableMap<String, @Value Object> variables;
    private final OptionalInt rowIndex;
    private final boolean recordExplanation;

    public EvaluateState(TypeManager typeManager, OptionalInt rowIndex)
    {
        this(makeVariables(rowIndex), typeManager, rowIndex, false);
    }

    public EvaluateState(TypeManager typeManager, OptionalInt rowIndex, boolean recordExplanation)
    {
        this(makeVariables(rowIndex), typeManager, rowIndex, recordExplanation);
    }

    private EvaluateState(ImmutableMap<String, @Value Object> variables, TypeManager typeManager, OptionalInt rowIndex, boolean recordExplanation)
    {
        this.variables = variables;
        this.typeManager = typeManager;
        this.rowIndex = rowIndex;
        this.recordExplanation = recordExplanation;
    }

    private static ImmutableMap<String, @Value Object> makeVariables(OptionalInt rowIndex)
    {
        return rowIndex.isPresent() ? ImmutableMap.<String, @Value Object>of(TypeState.ROW_NUMBER, DataTypeUtility.<Integer>value(rowIndex.getAsInt() + 1)) : ImmutableMap.<String, @Value Object>of();
    }

    public EvaluateState add(String varName, @Value Object value) throws InternalException
    {
        HashMap<String, @Value Object> copy = new HashMap<>();
        if (!varName.startsWith("?") && variables.containsKey(varName))
        {
            throw new InternalException("Duplicate variable name: " + varName);
        }
        copy.putAll(variables);
        copy.put(varName, value);
        return new EvaluateState(ImmutableMap.copyOf(copy), typeManager, rowIndex, recordExplanation);
    }

    /**
     * Gets value of variable.  Throws InternalException if variable not found
     * (since if we passed the type check, variable must be present during execution).
     */
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

    @SuppressWarnings("units")
    public @TableDataRowIndex int getRowIndex() throws UserException
    {
        return rowIndex.orElseThrow(() -> new UserException("No row index available."));
    }
    
    public OptionalInt _test_getOptionalRowIndex()
    {
        return rowIndex;
    }
    
    public ImmutableMap<String, @Value Object> _test_getVariables()
    {
        return variables;
    }

    public boolean recordExplanation()
    {
        return recordExplanation;
    }

    public EvaluateState varFilteredTo(ImmutableSet<String> variableNames)
    {
        return new EvaluateState(ImmutableMap.<String, @Value Object>copyOf(Maps.<String, @Value Object>filterEntries(variables, (Entry<String, @Value Object> e) -> e != null && variableNames.contains(e.getKey()))), typeManager, rowIndex, recordExplanation);
    }

    // Equals and hashCode on EvaluateState are only used by
    // explanations, for checking if two executations of the same
    // expression had equivalent context.
    @Override
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EvaluateState that = (EvaluateState) o;
        if (!variables.keySet().equals(that.variables.keySet()))
            return false;
        for (Entry<String, @Value Object> var : variables.entrySet())
        {
            @Value Object otherVarValue = that.variables.get(var.getKey());
            // Shouldn't be null given the above keySet check, but satisfy checker:
            if (otherVarValue == null)
                continue;
            try
            {
                if (Utility.compareValues(var.getValue(), otherVarValue) != 0)
                    return false;
            }
            catch (InternalException | UserException e)
            {
                Log.log(e);
                return false;
            }
        }
        return rowIndex.equals(that.rowIndex);
    }

    @Override
    public int hashCode()
    {
        // We don't hash variables because it's too complex, so
        // we just live with having hash collisions:
        return Objects.hash(rowIndex);
    }
}
