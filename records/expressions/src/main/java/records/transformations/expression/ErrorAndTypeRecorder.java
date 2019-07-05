package records.transformations.expression;

import annotation.recorded.qual.Recorded;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.transformations.expression.Expression.CheckedExp;
import records.transformations.expression.Expression.ExpressionKind;
import records.transformations.expression.function.FunctionLookup;
import records.typeExp.TypeConcretisationError;
import records.typeExp.TypeExp;
import records.typeExp.TypeExp.TypeError;
import styled.StyledShowable;
import styled.StyledString;
import utility.Either;
import utility.Pair;

import java.util.List;
import java.util.function.Consumer;

/**
 * A listener that records where errors have occurred when checking an expression.
 * 
 * Note: it is valid to record an error and/or quick fixes multiple times
 * for the same expression.  In this case, the error and list of quick fixes
 * should be concatenated to form the final outcome.
 */
public interface ErrorAndTypeRecorder
{
    /**
     * Checks the given error-or-type.  If error, that error is recorded 
     * (with no available quick fixes) and null is returned.
     * If type, it is returned directly.
     * @param src
     * @param errorOrType
     * @return
     */
    public default @Nullable TypeExp recordError(Expression src, Either<@Nullable TypeError, TypeExp> errorOrType)
    {
        return errorOrType.<@Nullable TypeExp>either(err -> {
            if (err != null)
            {
                recordError(src, err.getMessage());
            }
            return null;
        }, val -> val);
    }

    /**
     * Records an error source and error message
     */
    public default <T> @Nullable T recordLeftError(TypeManager typeManager, FunctionLookup functionLookup, @Recorded Expression src, Either<TypeConcretisationError, T> errorOrVal)
    {
        return errorOrVal.<@Nullable T>either(err -> {
            @Nullable DataType fix = err.getSuggestedTypeFix();
            recordError(src, err.getErrorText());
            recordQuickFixes(src, ExpressionUtil.quickFixesForTypeError(typeManager, functionLookup, src, fix));
            return null;
        }, val -> val);
    }

    /**
     * Records the type for a particular expression.
     */
    public default @Recorded @Nullable TypeExp recordType(Expression expression, @Nullable TypeExp typeExp)
    {
        if (typeExp != null)
            return recordTypeNN(expression, typeExp);
        else
            return null;
    }

    public default @Nullable CheckedExp recordType(Expression expression, TypeState typeState, @Nullable TypeExp typeExp)
    {
        if (typeExp != null)
            return new CheckedExp(recordTypeNN(expression, typeExp), typeState);
        else
            return null;
    }

    /**
     * A curried version of the two-arg function of the same name.
     */
    @Pure
    public default Consumer<StyledString> recordErrorCurried(Expression src)
    {
        return errMsg -> recordError(src, errMsg);
    }

    /**
     * Records an error source and error message.
     */
    public <EXPRESSION> void recordError(EXPRESSION src, StyledString error);

    /**
     * Records an source and information message.
     */
    public <EXPRESSION extends StyledShowable> void recordInformation(@Recorded EXPRESSION src, Pair<StyledString, @Nullable QuickFix<EXPRESSION>> informaton);
    
    public <EXPRESSION extends StyledShowable> void recordQuickFixes(@Recorded EXPRESSION src, List<QuickFix<EXPRESSION>> fixes);

    public default @Nullable CheckedExp recordTypeAndError(Expression expression, Either<@Nullable TypeError, TypeExp> typeOrError, TypeState typeState)
    {
        return recordTypeAndError(expression, expression, typeOrError, typeState);
    }
    
    public default @Nullable CheckedExp recordTypeAndError(Expression typeExpression, Expression errorExpression,Either<@Nullable TypeError, TypeExp> typeOrError, TypeState typeState)
    {
        @Nullable @Recorded TypeExp typeExp = recordType(typeExpression, recordError(errorExpression, typeOrError));
        if (typeExp == null)
            return null;
        else
            return new CheckedExp(typeExp, typeState);
    }

    public @Recorded @NonNull TypeExp recordTypeNN(Expression expression, @NonNull TypeExp typeExp);

}
