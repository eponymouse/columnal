package records.types;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.checkerframework.checker.nullness.qual.Nullable;
import styled.StyledString;
import utility.Utility;

import java.util.stream.Collectors;
import java.util.stream.Stream;

// An immutable set of type-classes which are required, but we carry more information
// about where the requirement comes from
public class TypeClassRequirements
{
    private static final TypeClassRequirements EMPTY = new TypeClassRequirements(ImmutableMap.of());

    public static TypeClassRequirements require(String typeClass, String functionName)
    {
        return new TypeClassRequirements(ImmutableMap.of(typeClass, new Context(ImmutableList.of(functionName))));
    }

    // Returns null if given set satisfies this requirement, or error if not.
    public @Nullable StyledString checkIfSatisfiedBy(StyledString typeName, ImmutableSet<String> typeClasses)
    {
        if (typeClasses.containsAll(this.typeClasses.keySet()))
            return null;
        else
            return StyledString.concat(typeName, StyledString.s(" is not " + Sets.difference(this.typeClasses.keySet(), typeClasses).stream().collect(Collectors.joining(" or "))));
    }

    private static class Context
    {
        private final ImmutableList<String> functionNames;

        private Context(ImmutableList<String> functionNames)
        {
            this.functionNames = functionNames;
        }

        public static Context merge(Context a, Context b)
        {
            return new Context(Utility.concatI(a.functionNames, b.functionNames));
        }
    }
    
    private final ImmutableMap<String, Context> typeClasses;

    public TypeClassRequirements(ImmutableMap<String, Context> typeClasses)
    {
        this.typeClasses = typeClasses;
    }

    public static TypeClassRequirements union(TypeClassRequirements a, TypeClassRequirements b)
    {
        ImmutableMap<String, Context> m = Stream.concat(a.typeClasses.entrySet().stream(), b.typeClasses.entrySet().stream())
                .collect(ImmutableMap.toImmutableMap(e -> e.getKey(), e -> e.getValue(), Context::merge));
        return new TypeClassRequirements(m);
    }
    
    public static TypeClassRequirements empty()
    {
        return EMPTY;
    }
}
