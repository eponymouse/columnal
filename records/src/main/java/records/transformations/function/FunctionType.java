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
 * or sub-date-types may be allowed/disallowed) and you can have
 * varargs in the last position.  Return type can also vary.
 *
 * This class is one possible overload of a function.
 */
public class FunctionType
{
    private final DataType returnType;
    private final List<DataType> fixedParams;
    private final @Nullable DataType varArg;
    private final Supplier<FunctionInstance> makeInstance;

    public FunctionType(Supplier<FunctionInstance> makeInstance, DataType returnType, DataType... fixedParams)
    {
        this.makeInstance = makeInstance;
        this.returnType = returnType;
        this.fixedParams = Arrays.asList(fixedParams);
        this.varArg = null;
    }

    public boolean matches(List<DataType> params)
    {
        if (varArg != null)
        {
            if (params.size() < fixedParams.size())
                return false;
            if (!fixedParams.equals(params.subList(0, fixedParams.size())))
                return false;
            DataType varArgFinal = varArg;
            if (!params.stream().skip(fixedParams.size()).allMatch(p -> varArgFinal.equals(p)))
                return false;
            return true;
        }
        else
        {
            return fixedParams.equals(params);
        }
    }

    public Pair<FunctionInstance, DataType> getFunctionAndReturnType()
    {
        return new Pair<>(makeInstance.get(), returnType);
    }
}
