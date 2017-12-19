package records.transformations.function;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.function.FunctionDefinition.FunctionTypes;
import records.transformations.function.FunctionDefinition.TypeMatcher;
import records.types.NumTypeExp;
import records.types.TypeCons;
import records.types.units.UnitExp;
import utility.Pair;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

/**
 * Base class for functions which take a single array of a numeric type
 * (possibly with units) and return a single value of that same type (incl. any units)
 */
public abstract class SingleNumericSummaryFunction extends FunctionDefinition
{
    public SingleNumericSummaryFunction(String name, Supplier<FunctionInstance> makeInstance)
    {
        super(name, makeInstance, () -> {
            NumTypeExp numType = new NumTypeExp(null, UnitExp.makeVariable());
            return new FunctionTypes(numType, new TypeCons(null, TypeCons.CONS_LIST, numType));
        });
    }

    @Override
    public <E> Pair<List<Unit>, E> _test_typeFailure(Random r, _test_TypeVary<E> newExpressionOfDifferentType, UnitManager unitManager) throws UserException, InternalException
    {
        return new Pair<>(Collections.emptyList(), newExpressionOfDifferentType.makeArrayExpression(ImmutableList.of(newExpressionOfDifferentType.getNonNumericType())));
    }
}
