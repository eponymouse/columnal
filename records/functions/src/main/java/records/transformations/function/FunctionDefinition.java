package records.transformations.function;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
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

    public FunctionDefinition(String name, TypeMatcher typeMatcher)
    {
        this.name = name;
        this.typeMatcher = typeMatcher;
    }

    public FunctionDefinition(String name, Supplier<FunctionInstance> makeInstance, DataType returnType, DataType paramType)
    {
        this.name = name;
        this.typeMatcher = new ExactType(makeInstance, returnType, paramType);
    }

    public FunctionTypes makeParamAndReturnType(TypeManager typeManager) throws InternalException
    {
        return typeMatcher.makeParamAndReturnType(typeManager);
    }
    
    /**
     * For autocompleting parameters: what are likely types to this function?
     */
    private @Nullable TypeExp getLikelyParamType()
    {
        return null; /*
        try
        {
            return typeMatcher.makeParamAndReturnType().paramType;
        }
        catch (InternalException e)
        {
            Utility.report(e);
            return null;
        }*/
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
        return ""; /*
        try
        {
            return typeMatcher.makeParamAndReturnType().returnType.toString();
        }
        catch (InternalException e)
        {
            Utility.report(e);
            return "";
        }*/
    }

    public String getName()
    {
        return name;
    }

    public static interface TypeMatcher
    {
        // We have to make it fresh for each type inference, because the params and return may
        // share a type variable which will get unified during the inference
        
        public FunctionTypes makeParamAndReturnType(TypeManager typeManager) throws InternalException;
    }

    // For functions which have no type variables (or unit variables) shared between params and return
    public static class ExactType implements TypeMatcher
    {
        private final DataType exactReturnType;
        private final DataType exactParamType;
        private final Supplier<FunctionInstance> makeInstance;


        public ExactType(Supplier<FunctionInstance> makeInstance, DataType exactReturnType, DataType exactParamType)
        {
            this.makeInstance = makeInstance;
            this.exactReturnType = exactReturnType;
            this.exactParamType = exactParamType;
        }

        @Override
        public FunctionTypes makeParamAndReturnType(TypeManager typeManager) throws InternalException
        {
            return new FunctionTypesUniform(typeManager, makeInstance, TypeExp.fromConcrete(null, exactReturnType), TypeExp.fromConcrete(null, exactParamType));
        }
    }


    public static abstract class FunctionTypes
    {
        public final TypeExp paramType;
        public final TypeExp returnType;
        protected final TypeManager typeManager;
        @MonotonicNonNull
        private FunctionInstance instance;

        FunctionTypes(TypeManager typeManager, TypeExp returnType, TypeExp paramType)
        {
            this.typeManager = typeManager;
            this.returnType = returnType;
            this.paramType = paramType;
        }
        
        protected abstract FunctionInstance makeInstanceAfterTypeCheck() throws UserException, InternalException;

        public final FunctionInstance getInstanceAfterTypeCheck() throws InternalException, UserException
        {
            if (instance == null)
            {
                instance = makeInstanceAfterTypeCheck();
            }
            return instance;
        }
    }
    
    // A version that has a single instance generator, regardless of type.
    public static class FunctionTypesUniform extends FunctionTypes
    {
        private final Supplier<FunctionInstance> makeInstance;

        FunctionTypesUniform(TypeManager typeManager, Supplier<FunctionInstance> makeInstance, TypeExp returnType, TypeExp paramType)
        {
            super(typeManager, returnType, paramType);
            this.makeInstance = makeInstance;
        }

        @Override
        protected FunctionInstance makeInstanceAfterTypeCheck()
        {
            return makeInstance.get();
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
        
        public TypeManager getTypeManager();
    }

    // For testing: give a unit list and parameter list that should fail typechecking
    public <E> Pair<List<Unit>, E> _test_typeFailure(Random r, _test_TypeVary<E> newExpressionOfDifferentType, UnitManager unitManager) throws UserException, InternalException
    {
        return new Pair<>(Collections.emptyList(), newExpressionOfDifferentType.getTypes(1, type ->
        {
            return TypeExp.unifyTypes(typeMatcher.makeParamAndReturnType(newExpressionOfDifferentType.getTypeManager()).paramType, TypeExp.fromConcrete(null, type.get(0))).isLeft();
        }).get(0));
    }
}
