package records.transformations.function;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;
import utility.Pair;

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
    private final DataType returnType;
    /**
     * If one item, that is the argument type.  If multiple, it's a tuple of those types.
     */
    private final DataType paramType;
    private final Supplier<FunctionInstance> makeInstance;

    public FunctionType(Supplier<FunctionInstance> makeInstance, DataType returnType, DataType paramType)
    {
        this.makeInstance = makeInstance;
        this.returnType = returnType;
        this.paramType = paramType;
    }

    public boolean matches(DataType actualType) throws InternalException, UserException
    {
        return DataType.checkSame(paramType, actualType, s -> {}) != null;
    }

    public Pair<FunctionInstance, DataType> getFunctionAndReturnType()
    {
        return new Pair<>(makeInstance.get(), returnType);
    }

    public DataType getParamType()
    {
        return paramType;
    }
}
