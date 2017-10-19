package records.transformations.function;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import utility.ExFunction;
import utility.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Created by neil on 11/12/2016.
 */
public abstract class FunctionDefinition
{
    private final String name;
    private final @LocalizableKey String shortDescriptionKey;

    FunctionDefinition(String name, @LocalizableKey String shortDescriptionKey)
    {
        this.name = name;
        this.shortDescriptionKey = shortDescriptionKey;
    }

    public @Nullable Pair<FunctionInstance, DataType> typeCheck(List<Unit> units, DataType param, Consumer<String> onError, UnitManager mgr) throws InternalException, UserException
    {
        if (!units.isEmpty())
        {
            onError.accept("Function \"" + getName() + "\" does not accept unit parameter");
            return null;
        }

        List<FunctionType> types = getOverloads(mgr);
        List<Pair<DataType, FunctionType>> possibilities = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (FunctionType functionType : types)
        {
            DataType t = functionType.checkType(param, e -> errors.add(e));
            if (t != null)
            {
                possibilities.add(new Pair<>(t, functionType));
            }
        }

        if (possibilities.size() == 0)
        {
            onError.accept("Function " + getName() + " cannot accept parameter of type " + param + "\nOverloads:\n" + errors.stream().collect(Collectors.joining("\n")));
            return null;
        }
        if (possibilities.size() > 1)
            throw new InternalException("Function " + getName() + " has multiple possible overloads for type " + param);

        return new Pair<>(possibilities.get(0).getSecond().getFunction(), possibilities.get(0).getFirst());
    }

    public String getName()
    {
        return name;
    }

    // Only for testing:
    public static interface _test_TypeVary<EXPRESSION>
    {
        public EXPRESSION getDifferentType(@Nullable DataType type) throws InternalException, UserException;
        public EXPRESSION getAnyType() throws UserException, InternalException;
        public EXPRESSION getNonNumericType() throws InternalException, UserException;

        public EXPRESSION getType(Predicate<DataType> mustMatch) throws InternalException, UserException;
        public List<EXPRESSION> getTypes(int amount, ExFunction<List<DataType>, Boolean> mustMatch) throws InternalException, UserException;

        public EXPRESSION makeArrayExpression(ImmutableList<EXPRESSION> items);
    }

    // For testing: give a unit list and parameter list that should fail typechecking
    public <E> Pair<List<Unit>, E> _test_typeFailure(Random r, _test_TypeVary<E> newExpressionOfDifferentType, UnitManager unitManager) throws UserException, InternalException
    {
        return new Pair<>(Collections.emptyList(), newExpressionOfDifferentType.getTypes(1, type ->
        {
            for (FunctionType functionType : getOverloads(unitManager))
            {
                if (functionType.checkType(type.get(0), s -> {}) != null)
                    return false;
            }
            return true;
        }).get(0));
    }

    public abstract List<FunctionType> getOverloads(UnitManager mgr) throws InternalException;

    /**
     * For auto-completion; what are common argument types for this function?
     * @param unitManager
     */
    public final List<DataType> getLikelyArgTypes(UnitManager unitManager) throws UserException, InternalException
    {
        List<DataType> r = new ArrayList<>();
        for (FunctionType functionType : getOverloads(unitManager))
        {
            @Nullable DataType t = functionType.getLikelyParamType();
            if (t != null)
                r.add(t);
        }
        return r;
    }

    public @LocalizableKey String getShortDescriptionKey()
    {
        return shortDescriptionKey;
    }
}
