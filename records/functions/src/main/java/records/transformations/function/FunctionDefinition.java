package records.transformations.function;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import records.data.datatype.DataType;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.types.TypeExp;
import utility.ExFunction;
import utility.Pair;
import utility.Utility;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * This class is one function, it will have a unique name, a single type and a single implementation
 */
public class FunctionDefinition
{
    private final String name;
    private final TypeMatcher typeMatcher;
    private final Supplier<FunctionInstance> makeInstance;

    public FunctionDefinition(String name, Supplier<FunctionInstance> makeInstance, TypeMatcher typeMatcher)
    {
        this.name = name;
        this.makeInstance = makeInstance;
        this.typeMatcher = typeMatcher;
    }

    public FunctionDefinition(String name, Supplier<FunctionInstance> makeInstance, DataType returnType, DataType paramType)
    {
        this.name = name;
        this.makeInstance = makeInstance;
        this.typeMatcher = new ExactType(returnType, paramType);
    }

    public FunctionInstance getFunction()
    {
        return makeInstance.get();
    }

    public FunctionTypes makeParamAndReturnType() throws InternalException
    {
        return typeMatcher.makeParamAndReturnType();
    }
    
    /**
     * For autocompleting parameters: what are likely types to this function?
     */
    private @Nullable TypeExp getLikelyParamType()
    {
        try
        {
            return typeMatcher.makeParamAndReturnType().paramType;
        }
        catch (InternalException e)
        {
            Utility.report(e);
            return null;
        }
    }

    /**
     * Gets the text to display when showing information about the argument type in the GUI.
     */
    public @Localized String getParamDisplay()
    {
        @Nullable TypeExp paramType = getLikelyParamType();
        return paramType == null ? "" : paramType.toString();
    }

    /**
     * Gets the text to display when showing information about the return type in the GUI.
     */
    public @Localized String getReturnDisplay()
    {
        try
        {
            return typeMatcher.makeParamAndReturnType().returnType.toString();
        }
        catch (InternalException e)
        {
            Utility.report(e);
            return "";
        }
    }

    public String getName()
    {
        return name;
    }

    public static interface TypeMatcher
    {
        // We have to make it fresh for each type inference, because the params and return may
        // share a type variable which will get unified during the inference
        
        public FunctionTypes makeParamAndReturnType() throws InternalException;
    }

    // For functions which have no type variables (or unit variables) shared between params and return
    public static class ExactType implements TypeMatcher
    {
        private final DataType exactReturnType;
        private final DataType exactParamType;
        

        public ExactType(DataType exactReturnType, DataType exactParamType)
        {
            this.exactReturnType = exactReturnType;
            this.exactParamType = exactParamType;
        }

        @Override
        public FunctionTypes makeParamAndReturnType() throws InternalException
        {
            return new FunctionTypes(TypeExp.fromConcrete(null, exactReturnType), TypeExp.fromConcrete(null, exactParamType));
        }
    }


    public static class FunctionTypes
    {
        public final TypeExp paramType;
        public final TypeExp returnType;


        FunctionTypes(TypeExp returnType, TypeExp paramType)
        {
            this.paramType = paramType;
            this.returnType = returnType;
        }
    }

    // Only for testing:
    public static interface _test_TypeVary<EXPRESSION>
    {
        public EXPRESSION getDifferentType(@Nullable TypeExp type) throws InternalException, UserException;
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
            return TypeExp.unifyTypes(typeMatcher.makeParamAndReturnType().paramType, TypeExp.fromConcrete(null, type.get(0))).isLeft();
        }).get(0));
    }
}
