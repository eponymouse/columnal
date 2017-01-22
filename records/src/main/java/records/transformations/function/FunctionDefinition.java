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
 * Created by neil on 11/12/2016.
 */
public abstract class FunctionDefinition
{
    private final String name;

    public FunctionDefinition(String name)
    {
        this.name = name;
    }

    public @Nullable Pair<FunctionInstance, DataType> typeCheck(List<Unit> units, DataType param, ExConsumer<String> onError, UnitManager mgr) throws InternalException, UserException
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
            if (type.matches(param))
                possibilities.add(type);
        }

        if (possibilities.size() == 0)
        {
            onError.accept("Function " + getName() + " cannot accept parameter of type " + param);
            return null;
        }
        if (possibilities.size() > 1)
            throw new InternalException("Function " + getName() + " has multiple possible overloads for type " + param);

        return possibilities.get(0).getFunctionAndReturnType();
    }

    public String getName()
    {
        return name;
    }

    // For testing: give a unit list and parameter list that should fail typechecking
    public Pair<List<Unit>, Expression> _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws UserException, InternalException
    {
        return new Pair<>(Collections.emptyList(), newExpressionOfDifferentType.getTypes(1, type ->
        {
            for (FunctionType functionType : getOverloads(unitManager))
            {
                if (functionType.matches(type.get(0)))
                    return false;
            }
            return true;
        }).get(0));
    }

    protected abstract List<FunctionType> getOverloads(UnitManager mgr) throws InternalException, UserException;

    /**
     * For auto-completion; what are common argument types for this function?
     * @param unitManager
     */
    public final List<DataType> getLikelyArgTypes(UnitManager unitManager) throws UserException, InternalException
    {
        return Utility.mapList(getOverloads(unitManager), t -> t.getParamType());
    }
}
