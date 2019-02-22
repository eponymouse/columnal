package records.transformations.expression;

import annotation.recorded.qual.Recorded;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.gui.expressioneditor.ExpressionEditorUtil;
import records.transformations.expression.Expression.CheckedExp;
import records.transformations.expression.Expression.ExpressionKind;
import records.transformations.expression.function.FunctionLookup;
import records.typeExp.TypeConcretisationError;
import records.typeExp.TypeExp;
import styled.StyledShowable;
import styled.StyledString;
import utility.Either;

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
    public default @Nullable TypeExp recordError(Expression src, Either<StyledString, TypeExp> errorOrType)
    {
        return errorOrType.<@Nullable TypeExp>either(err -> {
            recordError(src, err);
            return null;
        }, val -> val);
    }

    /**
     * Records an error source and error message
     */
    public default <T> @Nullable T recordLeftError(TypeManager typeManager, FunctionLookup functionLookup, Expression src, Either<TypeConcretisationError, T> errorOrVal)
    {
        return errorOrVal.<@Nullable T>either(err -> {
            @Nullable DataType fix = err.getSuggestedTypeFix();
            recordError(src, err.getErrorText());
            recordQuickFixes(src, ExpressionEditorUtil.quickFixesForTypeError(typeManager, functionLookup, src, fix));
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

    public default @Nullable CheckedExp recordType(Expression expression, ExpressionKind expressionKind, TypeState typeState, @Nullable TypeExp typeExp)
    {
        if (typeExp != null)
            return new CheckedExp(recordTypeNN(expression, typeExp), typeState, expressionKind);
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
     * Records an error source and error message, and a list of possible quick fixes
     */
    // TODO make the String @Localized
    public <EXPRESSION> void recordError(EXPRESSION src, StyledString error);
    
    public <EXPRESSION extends StyledShowable, SEMANTIC_PARENT> void recordQuickFixes(EXPRESSION src, List<QuickFix<EXPRESSION, SEMANTIC_PARENT>> fixes);

    public default @Nullable CheckedExp recordTypeAndError(Expression expression, Either<StyledString, TypeExp> typeOrError, ExpressionKind expressionKind, TypeState typeState)
    {
        @Nullable @Recorded TypeExp typeExp = recordType(expression, recordError(expression, typeOrError));
        if (typeExp == null)
            return null;
        else
            return new CheckedExp(typeExp, typeState, expressionKind);
    }

    public @Recorded @NonNull TypeExp recordTypeNN(Expression expression, @NonNull TypeExp typeExp);

}
