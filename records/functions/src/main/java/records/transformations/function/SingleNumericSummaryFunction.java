package records.transformations.function;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.function.FunctionType.FunctionTypes;
import records.transformations.function.FunctionType.TypeMatcher;
import records.types.NumTypeExp;
import records.types.TypeCons;
import records.types.units.UnitExp;
import utility.Pair;

import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Base class for functions which take a single array of a numeric type
 * (possibly with units) and return a single value of that same type (incl. any units)
 */
public abstract class SingleNumericSummaryFunction extends FunctionGroup
{
    public SingleNumericSummaryFunction(String name, @LocalizableKey String shortDescripKey)
    {
        super(name, shortDescripKey);
    }

    @Override
    public List<FunctionType> getOverloads(UnitManager mgr) throws InternalException
    {
        TypeMatcher anyUnit = () -> {
            NumTypeExp numType = new NumTypeExp(null, UnitExp.makeVariable());
            return new FunctionTypes(numType, new TypeCons(null, TypeCons.CONS_LIST, numType));
        };
        return Collections.singletonList(new FunctionType(this::makeInstance, anyUnit, null /* No need if only one overload */));
    }

    protected abstract FunctionInstance makeInstance();

    @Override
    public <E> Pair<List<Unit>, E> _test_typeFailure(Random r, _test_TypeVary<E> newExpressionOfDifferentType, UnitManager unitManager) throws UserException, InternalException
    {
        return new Pair<>(Collections.emptyList(), newExpressionOfDifferentType.makeArrayExpression(ImmutableList.of(newExpressionOfDifferentType.getNonNumericType())));
    }
}
