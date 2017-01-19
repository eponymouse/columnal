package records.transformations.function;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.ArrayExpression;
import records.transformations.expression.Expression;
import records.transformations.expression.Expression._test_TypeVary;
import utility.ExConsumer;
import utility.Pair;

import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Base class for functions which take a single array of a numeric type
 * (possibly with units) and return a single value of that same type (incl. any units)
 */
public abstract class SingleNumericSummaryFunction extends FunctionDefinition
{
    public SingleNumericSummaryFunction(String name)
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
        if (!paramType.isArray() || paramType.getMemberType().isEmpty() || !paramType.getMemberType().get(0).isNumber())
        {
            onError.accept("Function \"" + getName() + "\" requires a parameter of non-empty list of numeric type");
            return null;
        }

        return new Pair<>(makeInstance(), paramType.getMemberType().get(0));
    }

    protected abstract FunctionInstance makeInstance();

    @Override
    public Pair<List<Unit>, List<Expression>> _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws UserException, InternalException
    {
        return new Pair<>(Collections.emptyList(), Collections.singletonList(new ArrayExpression(ImmutableList.of(newExpressionOfDifferentType.getNonNumericType()))));
    }
}
