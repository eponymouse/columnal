package records.transformations.function;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.DataType.TypeRelation;
import records.error.InternalException;
import records.error.UserException;
import utility.ExConsumer;
import utility.Utility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/**
 * Function types are a lot like Java.  You can overload a function
 * and have varying numbers of parameters in the overloads.
 * Each parameter is a specific type (although the units can vary,
 * or sub-date-types may be allowed/disallowed) .
 * Return type can also vary.
 *
 * This class is one possible overload of a function.
 */
public class FunctionType
{
    private final TypeMatcher typeMatcher;
    private final Supplier<FunctionInstance> makeInstance;

    public FunctionType(Supplier<FunctionInstance> makeInstance, TypeMatcher typeMatcher)
    {
        this.makeInstance = makeInstance;
        this.typeMatcher = typeMatcher;
    }

    public FunctionType(Supplier<FunctionInstance> makeInstance, DataType returnType, DataType paramType)
    {
        this.makeInstance = makeInstance;
        this.typeMatcher = new TypeMatcher()
        {
            @Override
            public @Nullable DataType checkType(DataType actualType, ExConsumer<String> onError) throws InternalException, UserException
            {
                if (DataType.checkSame(paramType, actualType, TypeRelation.EXPECTED_A, onError) != null)
                    return returnType;
                return null;
            }

            @Override
            public DataType getLikelyParamType()
            {
                return paramType;
            }
        };
    }

    public FunctionInstance getFunction()
    {
        return makeInstance.get();
    }

    public @Nullable DataType checkType(DataType paramType, ExConsumer<String> onError) throws InternalException, UserException
    {
        return typeMatcher.checkType(paramType, onError);
    }

    /**
     * For autocompleting parameters: what are likely types to this function?
     */
    public @Nullable DataType getLikelyParamType()
    {
        return typeMatcher.getLikelyParamType();
    }

    public static interface TypeMatcher
    {
        public @Nullable DataType checkType(DataType paramType, ExConsumer<String> onError) throws InternalException, UserException;

        public @Nullable DataType getLikelyParamType();
    }

    public static class ExactType implements TypeMatcher
    {
        private final DataType exactType;

        public ExactType(DataType exactType)
        {
            this.exactType = exactType;
        }

        @Override
        public @Nullable DataType checkType(DataType paramType, ExConsumer<String> onError) throws InternalException, UserException
        {
            return DataType.checkSame(exactType, paramType, onError);
        }

        @Override
        public DataType getLikelyParamType()
        {
            return exactType;
        }
    }

    public static class ArrayType implements TypeMatcher
    {
        private final TypeMatcher matchInner;

        public ArrayType(TypeMatcher matchInner)
        {
            this.matchInner = matchInner;
        }

        @Override
        public @Nullable DataType checkType(DataType paramType, ExConsumer<String> onError) throws InternalException, UserException
        {
            if (paramType.isArray())
            {
                List<DataType> paramMemberType = paramType.getMemberType();
                if (paramMemberType.isEmpty())
                {
                    onError.accept("Cannot apply function to empty array");
                    return null;
                }
                return matchInner.checkType(paramMemberType.get(0), onError);
            }
            onError.accept("Expected array but found: " + paramType);
            return null;
        }

        @Override
        public @Nullable DataType getLikelyParamType()
        {
            @Nullable DataType type = matchInner.getLikelyParamType();
            if (type != null)
                return DataType.array(type);
            else
                return null;
        }
    }

    public static class TupleType implements TypeMatcher
    {
        private final List<TypeMatcher> matchInner;
        private final int reducesTo;

        public TupleType(int reducesTo, TypeMatcher... matchInner)
        {
            this.reducesTo = reducesTo;
            this.matchInner = Arrays.asList(matchInner);
        }

        @Override
        public @Nullable DataType checkType(DataType paramType, ExConsumer<String> onError) throws InternalException, UserException
        {
            if (paramType.isTuple() && paramType.getMemberType().size() == matchInner.size())
            {
                List<DataType> r = new ArrayList<>();
                for (int i = 0; i < matchInner.size(); i++)
                {
                    @Nullable DataType type = matchInner.get(i).checkType(paramType.getMemberType().get(i), onError);
                    if (type == null)
                        return null;
                    r.add(type);
                }
                return reducesTo == -1 ? DataType.tuple(r) : r.get(reducesTo);
            }
            onError.accept("Expected tuple size " + matchInner.size() + " but found: " + paramType);
            return null;
        }

        @Override
        public @Nullable DataType getLikelyParamType()
        {
            List<DataType> r = new ArrayList<>();
            for (TypeMatcher typeMatcher : matchInner)
            {
                @Nullable DataType t = typeMatcher.getLikelyParamType();
                if (t == null)
                    return null;

                r.add(t);
            }
            return DataType.tuple(r);
        }
    }

    /**
     * Takes a numeric type with any units, and this also forms the return type.
     */
    public static class NumberAnyUnit implements TypeMatcher
    {
        @Override
        public @Nullable DataType checkType(DataType paramType, ExConsumer<String> onError) throws InternalException, UserException
        {
            if (paramType.isNumber())
                return paramType;

            onError.accept("Expected numeric type but found: " + paramType);
            return null;
        }

        @Override
        public DataType getLikelyParamType()
        {
            return DataType.NUMBER;
        }
    }

    public static class AnyType implements TypeMatcher
    {

        @Override
        public @Nullable DataType checkType(DataType paramType, ExConsumer<String> onError) throws InternalException, UserException
        {
            return paramType;
        }

        @Override
        public @Nullable DataType getLikelyParamType()
        {
            return null;
        }
    }
}
