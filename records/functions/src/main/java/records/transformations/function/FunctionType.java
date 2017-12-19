package records.transformations.function;

import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import records.data.datatype.DataType;
import records.data.datatype.DataType.TypeRelation;
import records.error.InternalException;
import records.error.UserException;
import records.types.TypeExp;
import utility.Pair;
import utility.Utility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * This class is one function, it will have a unique name, a single type and a single implementation
 */
public class FunctionType
{
    private final String name;
    private final TypeMatcher typeMatcher;
    private final Supplier<FunctionInstance> makeInstance;
    private final @Nullable @LocalizableKey String overloadDescriptionKey;

    public FunctionType(String name, Supplier<FunctionInstance> makeInstance, TypeMatcher typeMatcher, @Nullable @LocalizableKey String descriptionKey)
    {
        this.name = name;
        this.makeInstance = makeInstance;
        this.typeMatcher = typeMatcher;
        this.overloadDescriptionKey = descriptionKey;
    }

    public FunctionType(String name, Supplier<FunctionInstance> makeInstance, DataType returnType, DataType paramType, @Nullable @LocalizableKey String descriptionKey)
    {
        this.name = name;
        this.makeInstance = makeInstance;
        this.overloadDescriptionKey = descriptionKey;
        this.typeMatcher = new ExactType(returnType, paramType);
    }

    public FunctionInstance getFunction()
    {
        return makeInstance.get();
    }

    /**
     * For autocompleting parameters: what are likely types to this function?
     */
    public @Nullable TypeExp getLikelyParamType()
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

    /**
     * Gets the localization key for the text which describes this particular overload of the function.
     * If no text is available for this overload (common when there is only one overload
     * of a function) then null is returned, and nothing should be displayed.
     */
    @Pure
    public @Nullable @LocalizableKey String getOverloadDescriptionKey()
    {
        return overloadDescriptionKey;
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
}
