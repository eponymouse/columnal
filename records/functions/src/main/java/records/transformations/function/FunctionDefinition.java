package records.transformations.function;

import annotation.funcdoc.qual.FuncDocKey;
import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.types.ExpressionBase;
import records.types.MutVar;
import records.types.TypeClassRequirements;
import records.types.TypeCons;
import records.types.TypeExp;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExFunction;
import utility.Pair;
import utility.SimulationFunction;
import utility.ValueFunction;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Random;
import java.util.ResourceBundle;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * This class is one function, it will have a unique name, a single type and a single implementation
 */
public abstract class FunctionDefinition
{
    // Namespace slash function name
    private final @FuncDocKey String funcDocKey;
    private final String name;
    private final TypeMatcher typeMatcher;
    private final @Localized String miniDescription;

    public FunctionDefinition(@FuncDocKey String funcDocKey) throws InternalException
    {
        this.funcDocKey = funcDocKey;
        this.name = extractName(funcDocKey);
        Details details = lookupFunction(name, funcDocKey);
        this.miniDescription = details.miniDescription;
        this.typeMatcher = details.typeMatcher;
    }

    private static String extractName(@FuncDocKey String funcDocKey)
    {
        String[] split = funcDocKey.split(":");
        return split[split.length - 1];
    }
    
    private static class Details
    {
        private final @Localized String miniDescription;
        private final TypeMatcher typeMatcher;

        private Details(@Localized String miniDescription, TypeMatcher typeMatcher)
        {
            this.miniDescription = miniDescription;
            this.typeMatcher = typeMatcher;
        }
    }
    
    private static Details lookupFunction(String functionName, @FuncDocKey String funcDocKey) throws InternalException
    {
        try
        {
            return new Details(
                ResourceBundle.getBundle("function_minis").getString(funcDocKey),
                parseFunctionType(functionName,
                    Arrays.asList(StringUtils.split(ResourceBundle.getBundle("function_typeargs").getString(funcDocKey), ";")),
                    Arrays.asList(StringUtils.split(ResourceBundle.getBundle("function_constraints").getString(funcDocKey), ";")),
                    ResourceBundle.getBundle("function_types").getString(funcDocKey)
                )
            );
        }
        catch (MissingResourceException e)
        {
            throw new InternalException("Missing information for " + funcDocKey, e);
        }
    }

    private static TypeMatcher parseFunctionType(String functionName, List<String> typeArgs, List<String> constraints, String functionType)
    {
        Map<String, TypeExp> typeVars = new HashMap<>();
        for (String typeArg : typeArgs)
        {
            TypeClassRequirements typeClassRequirements = TypeClassRequirements.empty();
            for (String constraint : constraints)
            {
                if (constraint.endsWith(" " + typeArg));
                {
                    typeClassRequirements = TypeClassRequirements.union(typeClassRequirements, 
                        TypeClassRequirements.require(StringUtils.removeEnd(constraint, " " + typeArg), functionName)); 
                }
            }
            
            typeVars.put(typeArg, new MutVar(null));
        }
        
        return typeManager -> {
            try
            {
                return new Pair<>(TypeExp.fromDataType(null, typeManager.loadTypeUse(functionType), typeVars::get), typeVars);
            }
            catch (UserException | InternalException e)
            {
                // It's us that wrote the type, so user exceptions become internal exceptions:
                throw new InternalException("Error in built-in function " + functionName, e);
            }
        };
    }


    public @Localized String getMiniDescription()
    {
        return miniDescription;
    }

    // Function type, and map from named typed vars to type expression
    public Pair<TypeExp, Map<String, TypeExp>> getType(TypeManager typeManager) throws InternalException
    {
        return typeMatcher.makeParamAndReturnType(typeManager);
    }
    
    @OnThread(Tag.Simulation)
    public abstract ValueFunction getInstance(SimulationFunction<String, DataType> paramTypes) throws InternalException, UserException;
    
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

    public @FuncDocKey String getDocKey()
    {
        return funcDocKey;
    }

    public static interface TypeMatcher
    {
        // We have to make it fresh for each type inference, because the params and return may
        // share a type variable which will get unified during the inference
        // Returns type of function, and type vars by name.
        public Pair<TypeExp, Map<String, TypeExp>> makeParamAndReturnType(TypeManager typeManager) throws InternalException;
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
    /*
    public <E> Pair<List<Unit>, E> _test_typeFailure(Random r, _test_TypeVary<E> newExpressionOfDifferentType, UnitManager unitManager) throws UserException, InternalException
    {
        return new Pair<>(Collections.emptyList(), newExpressionOfDifferentType.getTypes(1, type ->
        {
            return TypeExp.unifyTypes(typeMatcher.makeParamAndReturnType(newExpressionOfDifferentType.getTypeManager()).paramType, TypeExp.fromConcrete(null, type.get(0))).isLeft();
        }).get(0));
    }
    */
}
