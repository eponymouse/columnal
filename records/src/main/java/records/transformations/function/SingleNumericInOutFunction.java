package records.transformations.function;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.Expression;
import records.transformations.expression.Expression._test_TypeVary;
import utility.ExConsumer;
import utility.Pair;

import java.util.Collections;
import java.util.List;
import java.util.Random;

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
            return null;
        }
        @Nullable DataType paramType = checkSingleParam(params, onError);
        if (paramType == null)
            return null;
        if (!paramType.isNumber())
        {
            onError.accept("Function \"" + getName() + "\" requires a parameter of numeric type");
            return null;
        }

        return new Pair<>(makeInstance(), paramType);
    }

    protected abstract FunctionInstance makeInstance();

    @Override
    public Pair<List<Unit>, List<Expression>> _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws UserException, InternalException
    {
        //TODO randomly pick from a few other options (e.g. zero param, 2 param, units)
        return new Pair<>(Collections.emptyList(), Collections.singletonList(newExpressionOfDifferentType.getNonNumericType()));
    }

    @Override
    public List<DataType> getLikelyArgTypes(UnitManager unitManager) throws UserException, InternalException
    {
        return Collections.singletonList(DataType.NUMBER);
    }
}
