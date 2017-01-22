package records.transformations.function;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
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
    private final List<DataType> fixedParams;
    private final Supplier<FunctionInstance> makeInstance;

    public FunctionType(Supplier<FunctionInstance> makeInstance, DataType returnType, DataType... fixedParams)
    {
        this.makeInstance = makeInstance;
        this.returnType = returnType;
        this.fixedParams = Arrays.asList(fixedParams);
    }

    public boolean matches(List<DataType> params)
    {
        // TODO this won't work properly with passing an empty list in
        return fixedParams.equals(params);
    }

    public Pair<FunctionInstance, DataType> getFunctionAndReturnType()
    {
        return new Pair<>(makeInstance.get(), returnType);
    }

    public DataType getFixedParams()
    {
        return fixedParams.size() == 1 ? fixedParams.get(0) : DataType.tuple(fixedParams);
    }
}
