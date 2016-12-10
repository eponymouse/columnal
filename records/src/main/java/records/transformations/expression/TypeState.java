package records.transformations.expression;

import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;
import records.loadsave.OutputBuilder;
import utility.ExConsumer;
import utility.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

/**
 * Created by neil on 29/11/2016.
 */
public class TypeState
{
    private final HashMap<String, DataType> variables;
    private final HashMap<String, DataType> tagTypes;

    public TypeState(Map<String, DataType> tagTypes)
    {
        this(new HashMap<>(), new HashMap<>(tagTypes));
    }

    private TypeState(HashMap<String, DataType> variables, HashMap<String, DataType> tagTypes)
    {
        this.variables = variables;
        this.tagTypes = tagTypes;
    }

    public @Nullable TypeState add(String varName, DataType type, ExConsumer<String> error) throws InternalException, UserException
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

    public static @Nullable TypeState checkAllSame(List<TypeState> typeStates, ExConsumer<String> onError) throws InternalException, UserException
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

    public @Nullable DataType findTaggedType(String typeName)
    {
        return tagTypes.get(typeName);
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

    public @Nullable TypeAndTagInfo findTaggedType(Pair<@Nullable String, String> tagName, ExConsumer<String> onError) throws InternalException, UserException
    {
        @Nullable String typeName = tagName.getFirst();
        @Nullable DataType type;
        if (typeName != null)
        {
            type = tagTypes.get(typeName);
            if (type == null)
            {
                onError.accept("Could not find tagged type: \"" + typeName + "\"");
                return null;
            }
        }
        else
        {
            // Try to infer.
            List<Entry<String, DataType>> matches = new ArrayList<>();
            for (Entry<String, DataType> entry : tagTypes.entrySet())
            {
                if (entry.getValue().hasTag(tagName.getSecond()))
                    matches.add(entry);
            }
            if (matches.size() == 0)
            {
                onError.accept("Could not find type for tag: \"" + tagName + "\"");
                return null;
            }
            else if (matches.size() > 1)
            {
                onError.accept("Multiple types match tag: \"" + tagName + "\" (" + matches.stream().map(Entry::getKey).collect(Collectors.joining(", ")) + ").  To select type, qualify the tag, e.g. \\" + OutputBuilder.quotedIfNecessary(matches.get(0).getKey()) + "\\" + OutputBuilder.quotedIfNecessary(tagName.getSecond()));
                return null;
            }
            type = matches.get(0).getValue();
        }

        Pair<Integer, @Nullable DataType> tagDetail = type.unwrapTag(tagName.getSecond());
        if (tagDetail.getFirst() == -1)
        {
            onError.accept("Type \"" + typeName + "\" does not have tag: \"" + tagName + "\"");
            return null;
        }

        return new TypeAndTagInfo(type, tagDetail.getFirst(), tagDetail.getSecond());
    }
}
