package records.transformations.expression;

import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Created by neil on 29/11/2016.
 */
public class TypeState
{
    private final HashMap<String, DataType> variables;

    public TypeState()
    {
        this(new HashMap<>());
    }

    private TypeState(HashMap<String, DataType> variables)
    {
        this.variables = variables;
    }

    public @Nullable TypeState add(String varName, DataType type, Consumer<String> error)
    {
        HashMap<String, DataType> copy = new HashMap<>(variables);
        if (copy.containsKey(varName))
        {
            error.accept("Duplicate variable name: " + varName);
            return null;
        }
        copy.put(varName, type);
        return new TypeState(copy);
    }

    public static @Nullable TypeState checkAllSame(List<TypeState> typeStates, Consumer<String> onError)
    {
        HashSet<TypeState> noDups = new HashSet<>(typeStates);
        if (noDups.size() == 1)
            return noDups.iterator().next();
        Iterator<TypeState> iterator = noDups.iterator();
        TypeState a = iterator.next();
        TypeState b = iterator.next();
        MapDifference<String, DataType> diff = Maps.difference(a.variables, b.variables);
        onError.accept("Differing variables, such as: " + diff.entriesDiffering().entrySet().stream().map(e -> e.getKey() + ": " + e.getValue().leftValue() + " vs " + e.getValue().rightValue()).collect(Collectors.joining(" and ")));
        return null;
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
}
