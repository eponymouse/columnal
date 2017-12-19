package records.transformations.function;

import org.checkerframework.checker.i18n.qual.LocalizableKey;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.function.FunctionType.FunctionTypes;
import records.transformations.function.FunctionType.TypeMatcher;
import records.types.NumTypeExp;
import records.types.units.UnitExp;
import utility.Pair;

import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Base class for functions which take a single numeric input,
 * and give an output of the same type, with matching units.
 *
 * package-visible
 */
abstract class SingleNumericInOutFunction extends FunctionGroup
{
    SingleNumericInOutFunction(String name, @LocalizableKey String shortDescripKey)
    {
        super(name, shortDescripKey);
    }

    @Override
    public List<FunctionType> getOverloads(UnitManager mgr) throws InternalException
    {
        TypeMatcher anyUnit = () -> {
            NumTypeExp numType = new NumTypeExp(null, UnitExp.makeVariable());
            return new FunctionTypes(numType, numType);
        };
        return Collections.singletonList(new FunctionType(this::makeInstance, anyUnit, null));
    }

    protected abstract FunctionInstance makeInstance();

    @Override
    public <E> Pair<List<Unit>, E> _test_typeFailure(Random r, _test_TypeVary<E> newExpressionOfDifferentType, UnitManager unitManager) throws UserException, InternalException
    {
        //TODO randomly pick from a few other options (e.g. zero param, 2 param, units)
        return new Pair<>(Collections.emptyList(), newExpressionOfDifferentType.getNonNumericType());
    }
}
