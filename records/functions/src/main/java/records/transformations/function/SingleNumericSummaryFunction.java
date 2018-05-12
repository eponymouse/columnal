package records.transformations.function;

import annotation.funcdoc.qual.FuncDocKey;
import records.error.InternalException;

/**
 * Base class for functions which take a single array of a numeric type
 * (possibly with units) and return a single value of that same type (incl. any units)
 */
public abstract class SingleNumericSummaryFunction extends FunctionDefinition
{
    public SingleNumericSummaryFunction(@FuncDocKey String funcDocKey) throws InternalException
    {
        super(funcDocKey);
    }

    /*
    @Override
    public <E> Pair<List<Unit>, E> _test_typeFailure(Random r, _test_TypeVary<E> newExpressionOfDifferentType, UnitManager unitManager) throws UserException, InternalException
    {
        return new Pair<>(Collections.emptyList(), newExpressionOfDifferentType.makeArrayExpression(ImmutableList.of(newExpressionOfDifferentType.getNonNumericType())));
    }
    */
}
