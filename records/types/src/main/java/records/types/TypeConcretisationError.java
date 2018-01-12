package records.types;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;

import java.util.Optional;

/**
 * Basically a wrapper for a String error, with a flag recording whether fixing the type
 * manually is a possible solution, and a suggested type in this case.
 */
public class TypeConcretisationError
{
    private final String errorText;
    private final boolean couldBeFixedByManuallySpecifyingType;
    // If non-null, a suggested type for the fix:
    private final @Nullable DataType suggestedTypeFix;

    public TypeConcretisationError(String errorText)
    {
        this.errorText = errorText;
        this.couldBeFixedByManuallySpecifyingType = false;
        this.suggestedTypeFix = null;
    }
    
    public TypeConcretisationError(String errorText, @Nullable DataType suggestedTypeFix)
    {
        this.errorText = errorText;
        this.couldBeFixedByManuallySpecifyingType = true;
        this.suggestedTypeFix = suggestedTypeFix;
    }

    public String getErrorText()
    {
        return errorText;
    }

    public @Nullable DataType getSuggestedTypeFix()
    {
        return suggestedTypeFix;
    }
}
