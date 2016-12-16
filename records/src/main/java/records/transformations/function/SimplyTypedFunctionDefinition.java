package records.transformations.function;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import utility.ExConsumer;
import utility.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by neil on 16/12/2016.
 */
public abstract class SimplyTypedFunctionDefinition extends FunctionDefinition
{
    public SimplyTypedFunctionDefinition(String name)
    {
        super(name);
    }

    @Override
    public final @Nullable Pair<FunctionInstance, DataType> typeCheck(List<Unit> units, List<DataType> params, ExConsumer<String> onError, UnitManager mgr) throws InternalException, UserException
    {
        if (!units.isEmpty())
        {
            onError.accept("Function \"" + getName() + "\" does not accept unit parameter");
            return null;
        }

        List<FunctionType> types = getOverloads(mgr);
        List<FunctionType> possibilities = new ArrayList<>();
        for (FunctionType type : types)
        {
            if (type.matches(params))
                possibilities.add(type);
        }

        if (possibilities.size() == 0)
        {
            onError.accept("Function cannot accept parameters of types (" + params.stream().map(t -> t.toString()).collect(Collectors.joining(", ")) + ")");
            return null;
        }
        if (possibilities.size() > 1)
            throw new InternalException("Function has multiple possible overloads for types (" + params.stream().map(t -> t.toString()).collect(Collectors.joining(", ")) + ")");

        return possibilities.get(0).getFunctionAndReturnType();
    }

    protected abstract List<FunctionType> getOverloads(UnitManager mgr) throws InternalException, UserException;
}
