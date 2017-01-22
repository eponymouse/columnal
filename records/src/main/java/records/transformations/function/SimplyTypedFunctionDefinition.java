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
import utility.Utility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
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
            onError.accept("Function " + getName() + " cannot accept parameters of types (" + params.stream().map(t -> t.toString()).collect(Collectors.joining(", ")) + ")");
            return null;
        }
        if (possibilities.size() > 1)
            throw new InternalException("Function " + getName() + " has multiple possible overloads for types (" + params.stream().map(t -> t.toString()).collect(Collectors.joining(", ")) + ")");

        return possibilities.get(0).getFunctionAndReturnType();
    }

    protected abstract List<FunctionType> getOverloads(UnitManager mgr) throws InternalException, UserException;

    @Override
    public List<DataType> getLikelyArgTypes(UnitManager unitManager) throws UserException, InternalException
    {
        return Utility.mapList(getOverloads(unitManager), t -> t.getFixedParams());
    }

    @Override
    public Pair<List<Unit>, List<Expression>> _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws UserException, InternalException
    {
        if (r.nextInt(10) == 0)
            return new Pair<>(Collections.singletonList(Unit.SCALAR), Collections.emptyList());
        int paramAmount = r.nextInt(5);
        return new Pair<>(Collections.emptyList(), newExpressionOfDifferentType.getTypes(paramAmount, types ->
            !getOverloads(unitManager).stream().anyMatch(ft -> ft.matches(types))
        ));
    }
}
