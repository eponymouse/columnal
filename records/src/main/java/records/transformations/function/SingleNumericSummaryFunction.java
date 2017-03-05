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
import records.transformations.function.FunctionType.ArrayType;
import records.transformations.function.FunctionType.NumberAnyUnit;
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
    public List<FunctionType> getOverloads(UnitManager mgr) throws InternalException
    {
        return Collections.singletonList(new FunctionType(this::makeInstance, new ArrayType(new NumberAnyUnit())));
    }

    protected abstract FunctionInstance makeInstance();

    @Override
    public Pair<List<Unit>, Expression> _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws UserException, InternalException
    {
        return new Pair<>(Collections.emptyList(), new ArrayExpression(ImmutableList.of(newExpressionOfDifferentType.getNonNumericType())));
    }
}
