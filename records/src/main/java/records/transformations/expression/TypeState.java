package records.transformations.expression;

import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import records.data.datatype.DataType;
import records.data.datatype.TypeId;
import records.data.datatype.TypeManager;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.loadsave.OutputBuilder;
import records.transformations.function.FunctionDefinition;
import records.transformations.function.FunctionList;
import utility.ExBiConsumer;
import utility.ExConsumer;
import utility.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The state used while type-checking expressions.
 *
 * It changes based on pattern matches, which introduce new variables.
 */
public class TypeState
{
    // If variable is in there but > size 1, means it is known but cannot be used because it has multiple types in different guards
    private final Map<String, Set<DataType>> variables;
    private final Map<String, FunctionDefinition> functions;
    private final TypeManager typeManager;
    private final UnitManager unitManager;

    public TypeState(UnitManager unitManager, TypeManager typeManager)
    {
        this(new HashMap<>(), typeManager, unitManager);
    }

    private TypeState(Map<String, Set<DataType>> variables, TypeManager typeManager, UnitManager unitManager)
    {
        this.variables = Collections.unmodifiableMap(variables);
        this.typeManager = typeManager;
        this.functions = FunctionList.FUNCTIONS.stream().collect(Collectors.<@NonNull FunctionDefinition, @NonNull String, @NonNull FunctionDefinition>toMap(FunctionDefinition::getName, Function.<FunctionDefinition>identity()));
        this.unitManager = unitManager;
    }

    public @Nullable TypeState add(String varName, DataType type, ExConsumer<String> error) throws InternalException, UserException
    {
        HashMap<String, Set<DataType>> copy = new HashMap<>(variables);
        if (copy.containsKey(varName))
        {
            error.accept("Duplicate variable name: " + varName);
            return null;
        }
        copy.put(varName, Collections.singleton(type));
        return new TypeState(copy, typeManager, unitManager);
    }

    /**
     *  Merges a set of type states from different pattern guards.
     *
     *  The semantics here are that duplicate variables are allowed, if they refer to a variable
     *  with the same type. (e.g. @case (4, x) @orcase (6, x))  If they have a different type,
     *  it's an error.
     *
     *  This is different semantics to the union function, which does not permit variables with
     *  the same name (this may be a bit confusing: intersect does allow duplicates, but union
     *  does not!)
     */

    public static TypeState intersect(List<TypeState> typeStates) throws InternalException, UserException
    {
        Map<String, Set<DataType>> mergedVars = new HashMap<>(typeStates.get(0).variables);
        for (int i = 1; i < typeStates.size(); i++)
        {
            for (Entry<String, Set<DataType>> entry : typeStates.get(i).variables.entrySet())
            {
                // If it's present in both sets, only keep if same type, otherwise mask:
                mergedVars.merge(entry.getKey(), entry.getValue(), (a, b) -> {
                    HashSet<DataType> merged = new HashSet<DataType>();
                    merged.addAll(a);
                    merged.addAll(b);
                    return merged;
                });
            }
        }
        return new TypeState(mergedVars, typeStates.get(0).typeManager, typeStates.get(0).unitManager);
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TypeState typeState = (TypeState) o;

        return variables.equals(typeState.variables);
    }

    @Override
    public int hashCode()
    {
        return variables.hashCode();
    }

    // If it's null, it's totally unknown
    // If it's > size 1, it should count as masked because it has different types in different guards
    public @Nullable Set<DataType> findVarType(String varName)
    {
        return variables.get(varName);
    }

    public Optional<FunctionDefinition> findFunction(String name)
    {
        return Optional.ofNullable(functions.get(name));
    }

    public static class TypeAndTagInfo
    {
        public final DataType wholeType;
        public final int tagIndex;
        public final @Nullable DataType innerType;

        public TypeAndTagInfo(DataType wholeType, int tagIndex, @Nullable DataType innerType)
        {
            this.wholeType = wholeType;
            this.tagIndex = tagIndex;
            this.innerType = innerType;
        }
    }

    public @Nullable TypeAndTagInfo findTaggedType(Pair<String, String> tagName, ExConsumer<String> onError) throws InternalException, UserException
    {
        String typeName = tagName.getFirst();
        @Nullable DataType type;
        type = typeManager.lookupType(typeName);
        if (type == null)
        {
            onError.accept("Could not find tagged type: \"" + typeName + "\"");
            return null;
        }

        Pair<Integer, @Nullable DataType> tagDetail = type.unwrapTag(tagName.getSecond());
        if (tagDetail.getFirst() == -1)
        {
            onError.accept("Type \"" + typeName + "\" does not have tag: \"" + tagName + "\"");
            return null;
        }

        return new TypeAndTagInfo(type, tagDetail.getFirst(), tagDetail.getSecond());
    }

    public UnitManager getUnitManager()
    {
        return unitManager;
    }

    /**
     * Merges a set of TypeState items which came from the same original typestate,
     * but got passed to different sub expressions in a single pattern (e.g. in a tuple pattern match),
     * and now needed to be merged.
     *
     * Performs some checks and puts them back together.
     *
     * @param typeStates
     * @return Null if there is a user-triggered problem (in which case onError will have been called)
     */
    @Pure
    public static @Nullable TypeState union(TypeState original, ExConsumer<String> onError, TypeState... typeStates) throws InternalException, UserException
    {
        if (typeStates.length == 0)
            throw new InternalException("Attempted to merge type states of zero size");
        else if (typeStates.length == 1)
            return typeStates[0];

        Map<String, Set<DataType>> allNewVars = new HashMap<>();

        for (TypeState typeState : typeStates)
        {
            // TypeManager and UnitManager should be constant:
            if (typeState.typeManager != original.typeManager)
                throw new InternalException("Type manager changed between different type states");
            if (typeState.unitManager != original.unitManager)
                throw new InternalException("Unit manager changed between different type states");
            // Functions shouldn't have changed:
            if (!typeState.functions.equals(original.functions))
                throw new InternalException("Functions changed between different type states");
            // Variables: we first remove all the variables which already existed
            // What is left must not overlap
            MapDifference<String, Set<DataType>> diff = Maps.difference(typeState.variables, original.variables);
            if (!diff.entriesOnlyOnRight().isEmpty())
                throw new InternalException("Altered type state is missing some original variables: " + diff.entriesOnlyOnRight());
            if (!diff.entriesDiffering().isEmpty())
                throw new InternalException("Altered type state has altered original variables: " + diff.entriesOnlyOnRight());

            // Now diff new vars with other new vars:
            diff = Maps.difference(diff.entriesOnlyOnLeft(), allNewVars);
            if (!diff.entriesDiffering().isEmpty() || !diff.entriesInCommon().isEmpty())
            {
                // TODO ideally offer a quick fix to rename and add an equality check in guard
                onError.accept("Duplicate variables in different parts of pattern: " + diff.entriesDiffering().keySet() + " " + diff.entriesInCommon().keySet());
                return null;
            }
            // Record the new vars (shouldn't overlap):
            allNewVars.putAll(diff.entriesOnlyOnLeft());
        }
        // Shouldn't be any overlap given earlier checks:
        allNewVars.putAll(original.variables);
        return new TypeState(allNewVars, original.typeManager, original.unitManager);
    }
}
