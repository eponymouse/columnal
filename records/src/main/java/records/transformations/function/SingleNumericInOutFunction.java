package records.transformations.function;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import utility.ExConsumer;
import utility.Pair;

import java.util.List;

/**
 * Base class for functions which take a single numeric input,
 * and give an output of the same type, with no units.
 *
 * package-visible
 */
abstract class SingleNumericInOutFunction extends FunctionDefinition
{
    SingleNumericInOutFunction(String name)
    {
        super(name);
    }

    @Override
    public @Nullable Pair<FunctionInstance, DataType> typeCheck(List<Unit> units, List<DataType> params, ExConsumer<String> onError, UnitManager mgr) throws InternalException, UserException
    {
        if (!units.isEmpty())
        {
            onError.accept("Function \"" + getName() + "\" does not accept unit parameter");
        }
        if (params.size() != 1 || !params.get(0).isNumber())
        {
            onError.accept("Function \"" + getName() + "\" takes exactly one parameter of numeric type");
        }

        return new Pair<>(makeInstance(), params.get(0));
    }

    protected abstract FunctionInstance makeInstance();
}
