package records.transformations.function;

import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.Expression;
import records.transformations.expression.Expression._test_TypeVary;
import records.transformations.function.FunctionType.NumberAnyUnit;
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
    SingleNumericInOutFunction(String name, @LocalizableKey String shortDescripKey)
    {
        super(name, shortDescripKey);
    }

    @Override
    public List<FunctionType> getOverloads(UnitManager mgr) throws InternalException
    {
        return Collections.singletonList(new FunctionType(this::makeInstance, new NumberAnyUnit(), null));
    }

    protected abstract FunctionInstance makeInstance();

    @Override
    public Pair<List<Unit>, Expression> _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws UserException, InternalException
    {
        //TODO randomly pick from a few other options (e.g. zero param, 2 param, units)
        return new Pair<>(Collections.emptyList(), newExpressionOfDifferentType.getNonNumericType());
    }
}
